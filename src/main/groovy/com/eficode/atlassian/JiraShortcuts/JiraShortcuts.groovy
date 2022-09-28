package com.eficode.atlassian.JiraShortcuts

import com.atlassian.applinks.api.ApplicationLink
import com.atlassian.applinks.api.ApplicationType
import com.atlassian.applinks.core.DefaultApplicationLinkService
import com.atlassian.applinks.core.link.DefaultApplicationLink
import com.atlassian.applinks.spi.auth.AuthenticationScenario
import com.atlassian.applinks.spi.link.ApplicationLinkDetails
import com.atlassian.applinks.spi.link.MutatingApplicationLinkService
import com.atlassian.applinks.spi.util.TypeAccessor
import com.atlassian.jira.component.ComponentAccessor
import com.onresolve.scriptrunner.runner.customisers.WithPlugin

@WithPlugin("com.atlassian.applinks.applinks-plugin")

class JiraShortcuts {

    MutatingApplicationLinkService appLinkService = ComponentAccessor.getOSGiComponentInstanceOfType(MutatingApplicationLinkService) as DefaultApplicationLinkService


    /**
     * Creates an application link to a remote application with "OAuth (impersonation)"
     * @param applicationType What type of remote application the link is going to, ex: BitbucketApplicationType, JiraApplicationType, ConfluenceApplicationType
     * @param linkName Name of the new application link
     * @param linkIsPrimary Should this the primary link of this type
     * @param remoteUrl Url of the remote application, including protocol and port
     * @param remoteAdminUser An admin username on the remote application
     * @param remoteAdminPw Corresponding password
     * @return Thre created application link
     */
    ApplicationLink createApplicationLink(Class<ApplicationType> applicationType, String linkName, boolean linkIsPrimary = true, String remoteUrl, String remoteAdminUser, String remoteAdminPw ) {

        /** Type examples:
         * import com.atlassian.applinks.api.application.bitbucket.BitbucketApplicationType
         * import com.atlassian.applinks.api.application.confluence.ConfluenceApplicationType
         * import com.atlassian.applinks.api.application.jira.JiraApplicationType
         */



        appLinkService.createReciprocalLink(new URI(remoteUrl), null,remoteAdminUser, remoteAdminPw)

        ApplicationLinkDetails.Builder builder = ApplicationLinkDetails.builder()
        builder.name(linkName)
        builder.rpcUrl(new URI(remoteUrl))
        builder.isPrimary(linkIsPrimary)
        ApplicationLinkDetails linkDetails = builder.build()


        DefaultApplicationLink appLink = appLinkService.createApplicationLink( ComponentAccessor.getOSGiComponentInstanceOfType(TypeAccessor).getApplicationType(applicationType), linkDetails) as DefaultApplicationLink

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

        return appLink

    }

    ApplicationLink getApplicationLink(String nameOrUrl) {




        return appLinkService.getApplicationLinks().find {it.name == nameOrUrl || it.displayUrl.toString() == nameOrUrl || it.rpcUrl.toString() == nameOrUrl} as ApplicationLink

    }

    boolean deleteApplicationLink(ApplicationLink appLink) {
        appLinkService.deleteReciprocatedApplicationLink(appLink)
        appLinkService.deleteApplicationLink(appLink)

        return appLinkService.getApplicationLink(appLink.id) == null
    }



}
