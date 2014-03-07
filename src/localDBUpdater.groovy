// TODO: stop executing scripts on error(?)

// Load environment config
def config = new ConfigSlurper().parse( new File('localDBUpdater.properties' ).toURL() )

// Main - START
def lastRevisionLog = new Logger( 'lastRevision.log' )
def localDBUpdaterLog = new Logger( 'localDBUpdater.log' )

def now = new Date().toTimestamp()
localDBUpdaterLog.appendText( "START: $now\r\n\r\n" )

// Get HEAD revision
def headRevision = SubversionUtil.getHeadRevision( config.svn.localWorkingCopyPath );

if( headRevision == null ){
    localDBUpdaterLog.appendText( 'Invalid HEAD revision returned. Process aborted.\r\n' )
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
    localDBUpdaterLog.appendText( "Last revision executed and HEAD revision are the same (r$headRevision). Nothing to update.\r\n\r\n" )
} else {
    // Check for new scripts
    fromRevision = lastRevision.toInteger() + 1     // bump up to next revision since 'svn log' is inclusive
    localDBUpdaterLog.appendText( "Checking revisions r$fromRevision:$headRevision...\r\n" )
    def scriptsToExecute = SubversionUtil.getScriptsToExecute( config.svn.localWorkingCopyPath, config.svn.relativeScriptPath, fromRevision, headRevision  )
    localDBUpdaterLog.appendText( "Number of scripts added/updated: $scriptsToExecute.size\r\n\r\n" )

    // Iterate over scripts to execute
    scriptsToExecute.each { svnExportURL ->
        // Fetch script to temp location
        def fetchedScriptOutput = SubversionUtil.exportScript( config.svn.root, svnExportURL );
        def tempScriptFilePath = LocalDBUpdater.extractLocalFilePath( fetchedScriptOutput )

        if( tempScriptFilePath != '' ){
            // Execute script
            localDBUpdaterLog.appendText( '-='.multiply(50) + '\r\n' )
            localDBUpdaterLog.appendText( "EXECUTE: $tempScriptFilePath\r\n" )
            localDBUpdaterLog.appendText( LocalDBUpdater.executeScript( tempScriptFilePath ) + '\r\n' )

            // remove temp script
            new File( tempScriptFilePath ).delete()
        }
    }
}

now = new Date().toTimestamp()
localDBUpdaterLog.appendText( "END: $now\r\n" )
localDBUpdaterLog.saveText()

lastRevisionLog.appendText( headRevision )
lastRevisionLog.saveText()
println "Results logged to $localDBUpdaterLog.logFileName"

// Display execution results to user.
LocalDBUpdater.viewLogInEditor( localDBUpdaterLog, config.editor )

// Main - END

// Class defs
class SubversionUtil {

    static def getHeadRevision( String localWorkingCopyPath ){
        def revision = null
        def svnInfoOutput = "svn info $localWorkingCopyPath -rHEAD".execute().getText()     // execute svn info
        def svnInfoMatcher = svnInfoOutput =~ /Revision: (\d+)/

        svnInfoMatcher.each { match, matchGroup ->
            revision = matchGroup
        }

        revision
    }

    static def getScriptsToExecute( String localWorkingCopyPath, String svnScriptPath, fromRevision, toRevision ){
        // Make list of all unique scripts that have been added/modified
        def svnLogOutput = "svn log -v $localWorkingCopyPath -r$fromRevision:$toRevision".execute().getText()   // execute svn log
        def svnLogMatcher = svnLogOutput =~ "[AM] ($svnScriptPath/[^ (]*sql)"
        def scriptsToUpdate = []

        svnLogMatcher.each { match, group ->
            if( ! scriptsToUpdate.contains( group ) ) {
                scriptsToUpdate.add( group )
            }
        }

        scriptsToUpdate.sort()
    }

    static String exportScript( svnRoot, svnExportURL ){
        def svnExportPrepend = "svn export --force $svnRoot/"
        def svnExportAppend = ' C:\\temp'
        (svnExportPrepend + svnExportURL + svnExportAppend).execute().getText()   // execute svn export
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

    // Write logText to file
    Boolean saveText(){
        new File( logFileName ).setText( logText )
        true
    }
}

class LocalDBUpdater {
    static String executeScript( scriptFilePath ){
        def sqlCommandPrepend = 'sqlcmd -S localhost -r0 -i '
        def sqlCommand = sqlCommandPrepend + scriptFilePath
        println "EXECUTING: $scriptFilePath"
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