// TODO: stop executing scripts on error(?)

// Load environment config
def config = new ConfigSlurper().parse( new File('localDBUpdater.properties' ).toURL() )
// Line break code differs between OS platforms (on Windows it is \r\n)
def lineBreak = System.getProperty("line.separator")

// Main - START
def now = new Date().toTimestamp()

def lastRevisionLog = new Logger( 'lastRevision.log' )
def localDBUpdaterLog = new Logger( 'localDBUpdater.log' )

localDBUpdaterLog.appendText( "START: $now$lineBreak$lineBreak" )

// Get HEAD revision
def headRevision = SubversionUtil.getHeadRevision( config.svn.localWorkingCopyPath );

if( headRevision == null ){
    localDBUpdaterLog.appendText( "Invalid HEAD revision returned. Process aborted.$lineBreak" )
    localDBUpdaterLog.saveText()
    LocalDBUpdater.viewLogInEditor( localDBUpdaterLog, config.editor )
    return      // abort
}

println "HEAD revision: $headRevision"

// Get last revision executed
lastRevision = lastRevisionLog.getText()

if( lastRevision == '' ){
    // should only hit on first run
    lastRevision = headRevision
}

if( lastRevision == headRevision ){
    localDBUpdaterLog.appendText( "Last revision executed and HEAD revision are the same (r$headRevision). Nothing to update.$lineBreak$lineBreak" )
} else {
    // Check for new scripts
    fromRevision = lastRevision.toInteger() + 1     // bump up to next revision since 'svn log' is inclusive
    localDBUpdaterLog.appendText( "Checking revisions r$fromRevision:$headRevision...$lineBreak" )
    def scriptsToExecute = SubversionUtil.getScriptsToExecute( config.svn.localWorkingCopyPath, config.svn.relativeScriptPath, fromRevision, headRevision  )
    localDBUpdaterLog.appendText( "Number of scripts added/updated: $scriptsToExecute.size$lineBreak$lineBreak" )

    // Iterate over scripts to execute
    scriptsToExecute.each { svnExportURL ->
        // Fetch script to temp location
        def fetchedScriptOutput = SubversionUtil.exportScript( config.svn.root, svnExportURL );
        def tempScriptFilePath = LocalDBUpdater.extractLocalFilePath( fetchedScriptOutput )

        if( tempScriptFilePath != '' ){
            // Execute script
            localDBUpdaterLog.appendText( '-='.multiply(50) + lineBreak )
            localDBUpdaterLog.appendText( "EXECUTE: $tempScriptFilePath$lineBreak" )
            localDBUpdaterLog.appendText( LocalDBUpdater.executeScript( tempScriptFilePath ) + lineBreak )

            // remove temp script
            new File( tempScriptFilePath ).delete()
        }
    }
}

now = new Date().toTimestamp()
localDBUpdaterLog.appendText( "END: $now$lineBreak" )
localDBUpdaterLog.saveText()
if( config.history.keepHistory ) {
    localDBUpdaterLog.appendToHistory( config.history.historyDirectoryName, lineBreak )
}

lastRevisionLog.appendText( headRevision )
lastRevisionLog.saveText()
println "Results logged to $localDBUpdaterLog.logFileName"

// Display execution results to user.
LocalDBUpdater.viewLogInEditor( localDBUpdaterLog, config.editor )

// Main - END

// Class defs
class SubversionUtil {

    static def getHeadRevision( String localWorkingCopyPath ){
        def svnInfoOutput = "svn info $localWorkingCopyPath -rHEAD --xml".execute().getText()     // execute svn info
        def svnInfoXml = new XmlSlurper().parseText( svnInfoOutput )
        svnInfoXml.entry.@revision.text()
    }

    // Get a list of all unique scripts that have been added/modified
    static def getScriptsToExecute( String localWorkingCopyPath, String svnScriptPath, fromRevision, toRevision ){
        Set scriptsToExecute = []
        def relativeScriptFilePattern = ~/$svnScriptPath.+sql$/

        def svnLogOutput = "svn log -v --xml $localWorkingCopyPath -r$fromRevision:$toRevision".execute().getText()   // execute svn log
        def svnLogXml = new XmlSlurper().parseText( svnLogOutput )

        svnLogXml.logentry.paths.path.each {    // iterate over each script path.
            String kind = it.@kind
            String action = it.@action

            // locate added/modified SQL scripts.
            if (kind == 'file' && (action == 'A' || action == 'M')) {
                relativeScriptFilePattern.matcher(it.text()).each { scriptsToExecute.add(it) }
            }
        }

        scriptsToExecute.sort()
    }

    static String exportScript( svnRoot, svnExportURL ){
        def svnExportPrepend = "svn export --force $svnRoot/"
        def svnExportAppend = System.getProperty('java.io.tmpdir')
        // Enclose script name in double-quotes to handle any spaces in filename
        (svnExportPrepend + "\"$svnExportURL\" " + svnExportAppend).execute().getText()   // execute svn export
    }

}

class Logger {
    private String logFileName
    private String logText = ''

    Logger( String theLogFileName ){
        logFileName = theLogFileName
    }

    Boolean appendText( theLogText ){
        logText += theLogText
        true
    }

    def getText(){
        def response = ''
        def logFile = new File( logFileName )

        if( logFile.exists() ){
            response = logFile.getText().trim()
        }

        response
    }

    // Write logText to today's file
    Boolean saveText(){
        def mainFile =  new File( logFileName )
        mainFile.setText( logText )

        true
    }

    // Append log data to current yearly file
    Boolean appendToHistory( historyDirectoryName, lineBreak) {

        def currentHistoryFileName = new Date().format( 'yyyy' ) + '_history.log'
        def historyDirectory = new File( historyDirectoryName ).mkdir()
        def historyFile = new File( "$historyDirectoryName/$currentHistoryFileName" )

        appendText( lineBreak + '*^'.multiply(50) + lineBreak )
        historyFile.append( "$lineBreak$logText" )

        true
    }
}

class LocalDBUpdater {
    static String executeScript( scriptFilePath ){
        def sqlCommandPrepend = 'sqlcmd -S localhost -r0 -i '
        // Enclose script name in double-quotes to handle any spaces in filename
        def sqlCommand = sqlCommandPrepend + "\"$scriptFilePath\""

        sqlCommand.execute().getText()      // execute sqlcmd
    }

    static String extractLocalFilePath( fetchedScriptOutput ){
        def filePath = ''
        def localFilePattern = ~/C:\\.*sql/
        localFilePattern.matcher( fetchedScriptOutput ).each { filePath = it }
        filePath
    }

    static def viewLogInEditor( Logger log, String editor = 'notepad' ) {
        "$editor $log.logFileName".execute()
    }

}