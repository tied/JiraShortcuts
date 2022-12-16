package com.eficode.atlassian.jiraShortcuts.tests

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
    JiraInstanceManagerRest jiraRest = new JiraInstanceManagerRest("admin", "admin", jiraBaseUrl)

    @Shared
    String jiraScriptPath = "src/test/groovy/com/eficode/atlassian/JiraShortcuts/tests/jiraLocalScripts/"

    def setupSpec() {


        Map linkCleanup = jiraRest.executeLocalScriptFile(new File(jiraScriptPath + "DeleteAllAppLinks.groovy"))
        assert linkCleanup.errors == null  && linkCleanup.success


    }

    def "test"() {

        expect:
        Map linkCrud = jiraRest.executeLocalScriptFile(new File(jiraScriptPath + "CrudBitbucketLink.groovy"))
        assert linkCrud.errors == null  && linkCrud.success && linkCrud.log.last().endsWith("Script END")
    }
}
