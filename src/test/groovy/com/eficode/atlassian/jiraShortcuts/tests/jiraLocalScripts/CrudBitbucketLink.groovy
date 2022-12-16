package com.eficode.atlassian.jiraShortcuts.tests.jiraLocalScripts

import com.atlassian.applinks.api.ApplicationLink
import com.atlassian.applinks.api.application.bitbucket.BitbucketApplicationType
import com.onresolve.scriptrunner.runner.customisers.WithPlugin


@WithPlugin("com.eficode.atlassian.JiraShortcuts")
import com.eficode.atlassian.jiraShortcuts.JiraShortcuts
import org.apache.log4j.Level
import org.apache.log4j.Logger


Logger log = Logger.getLogger("create.bb.app.link")
log.setLevel(Level.ALL)


JiraShortcuts jc = new JiraShortcuts()

ApplicationLink link  = jc.createApplicationLink(BitbucketApplicationType, "Bitbucket", true, "http://bitbucket.domain.se:7990", "admin", "admin")

log.info("Created link:" + link.toString())

assert jc.deleteApplicationLink(link)

log.info("Deleted link:" + link)

log.info("Script END")
