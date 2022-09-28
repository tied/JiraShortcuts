package com.eficode.atlassian.JiraShortcuts.tests.jiraLocalScripts

import com.onresolve.scriptrunner.runner.customisers.WithPlugin

@WithPlugin("com.eficode.atlassian.JiraShortcuts")
import com.eficode.atlassian.JiraShortcuts.JiraShortcuts
import org.apache.log4j.Level
import org.apache.log4j.Logger


Logger log = Logger.getLogger("delete.app.links")
log.setLevel(Level.ALL)


JiraShortcuts jc = new JiraShortcuts()

jc.appLinkService.getApplicationLinks().each { appLink ->

    log.info("Deleting application link:" + appLink.toString())

    jc.deleteApplicationLink(appLink)
}

