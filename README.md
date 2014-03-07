Local Database Updater
======================

## About ##
This is a Groovy program that will execute newly added or updated database scripts based on a target repository and local directory.  It is written specifically for **Subversion** and **SQL Server**. This program works on the assumption that all committed database scripts are twice-run safe.  I.e., a script can be executed multiple times without any adverse effects.

## Configuration ##

Before first run you will need to copy **localDBUpdater.properties.template** to **localDBUpdater.properties** and update the configuration with your own settings.

## Running ##

**Compile:**
groovyc localDBUpdater.groovy

**Execute:**
groovy localDBUpdater

## First Run ##
On first run a file named **lastRevision.log** will be created and will store the HEAD revision number of your repository.  Not much else will happen on this first run.  On all consecutive runs any newly added or updated scripts to the repository since your last run will be executed for the locally installed SQL Server instance.

Execution output is logged to a file named localDBUpdater.log.

**Tip:** After your first run you can update the revision number set in **lastRevision.log** to an older revision if you need to pick up and execute any older scripts.