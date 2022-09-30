package com.eficode.atlassian.JiraShortcuts

import com.atlassian.applinks.api.ApplicationLink
import com.atlassian.applinks.api.ApplicationLinkRequestFactory
import com.atlassian.applinks.api.ApplicationLinkResponseHandler
import com.atlassian.applinks.api.ApplicationType
import com.atlassian.applinks.api.application.bitbucket.BitbucketApplicationType
import com.atlassian.applinks.core.DefaultApplicationLinkService
import com.atlassian.applinks.core.auth.ApplicationLinkAnalyticsRequest
import com.atlassian.applinks.core.link.DefaultApplicationLink
import com.atlassian.applinks.spi.auth.AuthenticationScenario
import com.atlassian.applinks.spi.link.ApplicationLinkDetails
import com.atlassian.applinks.spi.link.MutatingApplicationLinkService
import com.atlassian.applinks.spi.util.TypeAccessor
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.sal.api.net.Request
import com.atlassian.sal.api.net.Response
import com.atlassian.sal.api.net.ResponseException
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import org.apache.log4j.Level
import org.apache.log4j.Logger


@WithPlugin("com.atlassian.applinks.applinks-plugin")

class JiraShortcuts {

    MutatingApplicationLinkService appLinkService = ComponentAccessor.getOSGiComponentInstanceOfType(MutatingApplicationLinkService) as DefaultApplicationLinkService
    JiraAuthenticationContext authContext = ComponentAccessor.getJiraAuthenticationContext()
    Logger log = Logger.getLogger(JiraShortcuts.class)


    JiraShortcuts() {
        log.setLevel(Level.ALL)
    }

    /**
     * Creates an application link to a remote application with "OAuth (impersonation)"
     * @param applicationType What type of remote application the link is going to, ex: BitbucketApplicationType, JiraApplicationType, ConfluenceApplicationType
     * @param linkName Name of the new application link
     * @param linkIsPrimary Should this the primary link of this type
     * @param remoteUrl Url of the remote application, including protocol and port
     * @param remoteAdminUser An admin username on the remote application
     * @param remoteAdminPw Corresponding password
     * @return The created application link
     */
    ApplicationLink createApplicationLink(Class<ApplicationType> applicationType, String linkName, boolean linkIsPrimary = true, String remoteUrl, String remoteAdminUser, String remoteAdminPw) {

        /** Type examples:
         * import com.atlassian.applinks.api.application.bitbucket.BitbucketApplicationType
         * import com.atlassian.applinks.api.application.confluence.ConfluenceApplicationType
         * import com.atlassian.applinks.api.application.jira.JiraApplicationType
         */



        appLinkService.createReciprocalLink(new URI(remoteUrl), null, remoteAdminUser, remoteAdminPw)

        ApplicationLinkDetails.Builder builder = ApplicationLinkDetails.builder()
        builder.name(linkName)
        builder.rpcUrl(new URI(remoteUrl))
        builder.isPrimary(linkIsPrimary)
        ApplicationLinkDetails linkDetails = builder.build()


        DefaultApplicationLink appLink = appLinkService.createApplicationLink(ComponentAccessor.getOSGiComponentInstanceOfType(TypeAccessor).getApplicationType(applicationType), linkDetails) as DefaultApplicationLink

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
                , remoteAdminUser, remoteAdminPw
        )

        return appLink

    }


    /**
     * Creates an application link to a remote Bitbucket instance with "OAuth (impersonation)"
     * @param linkName Name of the new application link
     * @param linkIsPrimary Should this the primary link of this type
     * @param remoteUrl Url of the remote application, including protocol and port
     * @param remoteAdminUser An admin username on the remote application
     * @param remoteAdminPw Corresponding password
     * @return Thre created application link
     */
    ApplicationLink createApplicationLinkToBitbucket(String linkName, boolean linkIsPrimary = true, String remoteUrl, String remoteAdminUser, String remoteAdminPw) {
        return createApplicationLink(BitbucketApplicationType, linkName, linkIsPrimary, remoteUrl, remoteAdminUser, remoteAdminPw)
    }

    ArrayList<ApplicationLink> getApplicationLinks() {
        return appLinkService.getApplicationLinks().toList()
    }

    ApplicationLink getApplicationLink(String nameOrUrl) {

        return getApplicationLinks().find { it.name == nameOrUrl || it.displayUrl.toString() == nameOrUrl || it.rpcUrl.toString() == nameOrUrl } as ApplicationLink

    }


    boolean deleteApplicationLink(ApplicationLink appLink) {
        appLinkService.deleteReciprocatedApplicationLink(appLink)
        appLinkService.deleteApplicationLink(appLink)

        return appLinkService.getApplicationLink(appLink.id) == null
    }




    Response appLinkRequest(Request.MethodType requestMethod, String url, String body = null, String contentType = null, ApplicationLink applicationLink, ApplicationUser requestUser = null) {

        ApplicationUser initialUser = authContext.getLoggedInUser()

        log.info("Executing REST API request using AppLink")
        log.debug("\tAppLink:" + applicationLink)
        log.debug("\tURL:" + url)
        log.debug("\tHTTP Method:" + requestMethod.toString())
        log.debug("\tAs user:" + requestUser ?: initialUser)

        Response response
        try {

            if (requestUser) {
                authContext.setLoggedInUser(requestUser)
            }

            ApplicationLinkRequestFactory linkRequestFactory = applicationLink.createAuthenticatedRequestFactory()
            ApplicationLinkAnalyticsRequest request = linkRequestFactory.createRequest(requestMethod, url) as ApplicationLinkAnalyticsRequest



            response = request.execute(new LinkRequestHandler())

            log.debug("\tRequest returned with status:" + response.statusCode)
            log.trace("\t\tResponse body as text:")
            response.responseBodyAsString.eachLine { log.trace("\t" * 3 + it) }


        } catch (ex) {
            log.error("Error executing REST request to url $url, using AppLink:" + applicationLink)
            log.error("\tException:" + ex.message)
            authContext.setLoggedInUser(initialUser)
            throw ex


        }
        authContext.setLoggedInUser(initialUser)
        return response


    }


    class LinkRequestHandler implements ApplicationLinkResponseHandler<Response> {

        @Override
        Response credentialsRequired(Response response) throws ResponseException {
            throw new ResponseException(response.toString())
        }

        @Override
        Response handle(Response response) throws ResponseException {
            return response
        }
    }


}
