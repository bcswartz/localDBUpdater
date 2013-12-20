Local Database Updater
======================

This is a Groovy program that will execute newly added or updated database scripts to a repository based on a target directory.  It is written specifically for Subversion and SQL Server.

Before first run you will need to update the configuration in localDBUpdater.properties with your own settings.

On first run a file named lastRevision.log will be created and will store the HEAD revision number of your repository.  Not much else happens on this first run.

On all consecutive runs any newly added or updated scripts to the repository since your last run will be executed for the locally installed SQL Server instance.  Execution output is logged to a file named localDBUpdater.log.

Note: this program works on the assumption that all committed database scripts are twice-run safe.  I.e., a script can be executed multiple times without any adverse effects.

Compile:
groovyc localDBUpdater.groovy

Execute:
groovy localDBUpdater