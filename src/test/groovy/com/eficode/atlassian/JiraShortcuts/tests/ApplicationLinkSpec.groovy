package com.eficode.atlassian.JiraShortcuts.tests

import com.eficode.atlassian.jiraInstanceManager.JiraInstanceManagerRest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

class ApplicationLinkSpec extends Specification{

    @Shared
    Logger log = LoggerFactory.getLogger(this.class)

    @Shared
    String jiraBaseUrl = "http://jira.domain.se:8080"

    @Shared
    String bitbucketBaseUrl = "http://bitbucket.domain.se:7990"

    @Shared
    JiraInstanceManagerRest jiraRest = new JiraInstanceManagerRest(jiraBaseUrl, "admin", "admin")



    def setupSpec() {




    }
}
