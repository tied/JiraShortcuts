package com.eficode.atlassian.JiraShortcuts

import com.atlassian.applinks.api.ApplicationLink
import com.atlassian.applinks.api.ApplicationLinkRequest
import com.atlassian.applinks.api.ApplicationLinkRequestFactory
import com.atlassian.applinks.api.ApplicationLinkResponseHandler
import com.atlassian.applinks.api.ApplicationType
import com.atlassian.applinks.api.auth.types.OAuthAuthenticationProvider
import com.atlassian.applinks.core.DefaultApplicationLinkService
import com.atlassian.applinks.api.application.bitbucket.BitbucketApplicationType
import com.atlassian.applinks.core.link.DefaultApplicationLink
import com.atlassian.applinks.core.property.PropertyService
import com.atlassian.applinks.core.property.SalPropertyService
import com.atlassian.applinks.spi.auth.AuthenticationScenario
import com.atlassian.applinks.spi.link.ApplicationLinkDetails
import com.atlassian.applinks.spi.link.MutatingApplicationLinkService
import com.atlassian.applinks.spi.util.TypeAccessor
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.sal.api.net.Request
import com.atlassian.sal.api.net.Response
import com.atlassian.sal.api.net.ResponseException
import com.onresolve.scriptrunner.canned.common.JsonParsingApplicationLinkResponseHandler
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import groovy.json.JsonSlurper
import groovy.transform.AutoImplement
import org.apache.log4j.Level
import org.apache.log4j.Logger
import com.atlassian.applinks.api.ApplicationLinkService

@WithPlugin("com.atlassian.applinks.applinks-plugin")

Logger log = Logger.getLogger("poc")
log.setLevel(Level.ALL)
log.info("RUNNING POC")
//DefaultApplicationLinkService appLinkService = ComponentAccessor.getComponentOfType(DefaultApplicationLinkService)


MutatingApplicationLinkService appLinkService = ComponentAccessor.getOSGiComponentInstanceOfType(MutatingApplicationLinkService) as DefaultApplicationLinkService
TypeAccessor typeAccessor = ComponentAccessor.getOSGiComponentInstanceOfType(TypeAccessor)
OAuthAuthenticationProvider oAuthAuthenticationProvider = ComponentAccessor.getComponentOfType(OAuthAuthenticationProvider)

PropertyService propertyService = ComponentAccessor.getComponent(SalPropertyService)


String remoteAdminUser = "admin"
String remoteAdminPw = remoteAdminUser

ApplicationLinkDetails.Builder builder = ApplicationLinkDetails.builder()
builder.name("applink-name")
builder.rpcUrl(new URI("http://bitbucket.domain.se:7990"))
builder.isPrimary(true)
ApplicationLinkDetails linkDetails = builder.build()

log.info("Creating")

appLinkService.createReciprocalLink(new URI("http://bitbucket.domain.se:7990"), null,remoteAdminUser, remoteAdminPw)
DefaultApplicationLink appLink = appLinkService.createApplicationLink( typeAccessor.getApplicationType(BitbucketApplicationType), linkDetails) as DefaultApplicationLink
//DefaultApplicationLink appLink = appLinkService.getPrimaryApplicationLink(BitbucketApplicationType)
appLink.createAuthenticatedRequestFactory(OAuthAuthenticationProvider)

appLinkService.configureAuthenticationForApplicationLink(
        appLink,
        new AuthenticationScenario() {
            public boolean isCommonUserBase() {
                return true;
            }

            public boolean isTrusted() {
                return true;
            }
        }
,remoteAdminUser, remoteAdminPw
)


ApplicationLinkRequestFactory linkRequestFactory = appLink.createAuthenticatedRequestFactory()

import  com.atlassian.applinks.core.auth.ApplicationLinkAnalyticsRequest
ApplicationLinkAnalyticsRequest request = linkRequestFactory.createRequest(Request.MethodType.GET, "/rest/api/latest/admin/cluster") as ApplicationLinkAnalyticsRequest

JsonParsingApplicationLinkResponseHandler jsonHandler = new JsonParsingApplicationLinkResponseHandler()
def handler = new ApplicationLinkResponseHandler<Map>() {
    @Override
    Map credentialsRequired(Response response) throws ResponseException {
        return null
    }

    @Override
    Map handle(Response response) throws ResponseException {
        assert response.statusCode == 200
        new JsonSlurper().parseText(response.getResponseBodyAsString()) as Map
    }
}

log.info("here")
def response = request.execute(handler)

log.info("linkRequestFactory:"+ response)

//appLinkService.deleteApplicationLink(appLink)

