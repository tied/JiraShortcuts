package com.eficode.atlassian.jiraShortcuts

import com.atlassian.applinks.api.ApplicationLink
import com.atlassian.applinks.api.ApplicationType
import com.atlassian.applinks.api.application.bitbucket.BitbucketApplicationType
import com.atlassian.applinks.core.DefaultApplicationLinkService
import com.atlassian.applinks.core.link.DefaultApplicationLink
import com.atlassian.applinks.spi.auth.AuthenticationScenario
import com.atlassian.applinks.spi.link.ApplicationLinkDetails
import com.atlassian.applinks.spi.link.MutatingApplicationLinkService
import com.atlassian.applinks.spi.util.TypeAccessor
import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.bc.issue.link.IssueLinkService
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.IssueTypeManager
import com.atlassian.jira.config.PriorityManager
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.*
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.jira.issue.link.IssueLinkManager
import com.atlassian.jira.issue.link.IssueLinkType
import com.atlassian.jira.issue.priority.Priority
import com.atlassian.jira.issue.search.SearchException
import com.atlassian.jira.issue.search.SearchResults
import com.atlassian.jira.project.Project
import com.atlassian.jira.project.ProjectManager
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.servicedesk.api.ServiceDeskManager
import com.atlassian.servicedesk.api.requesttype.RequestType
import com.atlassian.servicedesk.api.requesttype.RequestTypeService
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import org.apache.commons.lang3.math.NumberUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger



@WithPlugin("com.atlassian.applinks.applinks-plugin")

class JiraShortcuts {

    MutatingApplicationLinkService appLinkService = ComponentAccessor.getOSGiComponentInstanceOfType(MutatingApplicationLinkService) as DefaultApplicationLinkService
    ProjectManager projectManager = ComponentAccessor.getProjectManager()
    IssueManager issueManager = ComponentAccessor.getIssueManager()
    IssueService issueService = ComponentAccessor.getIssueService()
    PriorityManager priorityManager = ComponentAccessor.getComponentOfType(PriorityManager)
    JiraAuthenticationContext authenticationContext = ComponentAccessor.getJiraAuthenticationContext()
    CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager()
    IssueTypeManager issueTypeManager = ComponentAccessor.getComponentOfType(IssueTypeManager)
    IssueLinkManager issueLinkManager = ComponentAccessor.getIssueLinkManager()
    IssueLinkService issueLinkService = ComponentAccessor.getComponentOfType(IssueLinkService)
    SearchService searchService = ComponentAccessor.getComponentOfType(SearchService)
    ServiceDeskManager serviceDeskManager = ComponentAccessor.getOSGiComponentInstanceOfType(ServiceDeskManager)
    RequestTypeService requestTypeService = ComponentAccessor.getOSGiComponentInstanceOfType(RequestTypeService)


    Logger log = Logger.getLogger(JiraShortcuts)

    public ApplicationUser serviceUser


    JiraShortcuts() {

        this.log.setLevel(Level.ALL)
        log.info("JiraShortcuts has started")

        serviceUser = authenticationContext.getLoggedInUser()
    }


    ArrayList<Issue> jql(String jql) {

        log.info("Will run JQL query:" + jql)
        SearchService.ParseResult parseResult = searchService.parseQuery(serviceUser, jql)

        if (!parseResult.valid) {
            String errorMsg = "The supplied JQL is invalid:" + jql + ". Error:" + parseResult.errors.errorMessages.join(",")
            log.error(errorMsg)
            throw new SearchException(errorMsg)
        }

        SearchResults searchResults = searchService.search(serviceUser, parseResult.query, PagerFilter.getUnlimitedFilter())
        return searchResults.getResults().collect { issueManager.getIssueByCurrentKey(it.key) }

    }


    private IssueInputParameters prepareIssueInput(Map issueParameters, Map customFieldValues) {

        String parameterValidationResult = validateIssueParameterMap(issueParameters)

        if (parameterValidationResult != null) {
            throw new InputMismatchException(parameterValidationResult)
        }

        long projectId = projectManager.getProjectByCurrentKey(issueParameters.projectKey as String)?.id
        String issueTypeId = issueTypeManager.getIssueTypes().find { it.name == issueParameters.issueTypeName }?.id


        IssueInputParameters issueInputParameters = issueService.newIssueInputParameters()


        issueInputParameters.setProjectId(projectId)
        issueInputParameters.setIssueTypeId(issueTypeId)
        issueInputParameters.setSummary(issueParameters.summary as String)
        if (issueParameters.containsKey("description")) {
            issueInputParameters.setDescription(issueParameters.description as String)
        }

        if (issueParameters.containsKey("priority")) {
            Priority priority

            if (NumberUtils.isParsable(issueParameters.priority as String) ) {
                priority = priorityManager.getPriority(issueParameters.priority as String)
            }else {
                priority = priorityManager.getPriorities().find {it.name == issueParameters.priority}
            }

            if (priority == null) {
                throw new InputMismatchException("Could not find Priority:" + issueParameters.priority)
            }
            issueInputParameters.setPriorityId(priority.id)
        }

        try {
            customFieldValues.each {

                String value
                if ([ArrayList, List].contains(it.value.class)) {
                    value = it.value.join(",")
                } else {
                    value = it.value
                }
                issueInputParameters.addCustomFieldValue(it.key, value)
            }

        } catch (all) {
            log.error(all.message)
            throw all
        }


        return issueInputParameters

    }

    static String validateIssueParameterMap(Map parameters) {

        ArrayList<String> requiredKeys = ["projectKey", "issueTypeName", "summary"]
        ArrayList<String> missingKeys = requiredKeys.findAll { requiredKey -> !parameters.containsKey(requiredKey) }

        if (!missingKeys.empty) {
            return "Issue input parameter map is missing:" + missingKeys.join(",")
        }


        return null

    }

    /**
     * Get the requestType object needed for setting the "Customer Request Type" field value
     * @param projectKey The project where the request type is located
     * @param requestTypeName Name of the request type
     * @return an VpOrigin that can be written to the field.
     */
    def getRequestTypeFieldValue(String projectKey, String requestTypeName) {

        log.debug("Getting Request type field value for request $requestTypeName in project $projectKey")

        CustomField requestTypeField = customFieldManager.getCustomFieldObjectsByName("Customer Request Type").first()
        log.debug("\tRequest type field:" + requestTypeField.toString())
        Project project = projectManager.getProjectByCurrentKeyIgnoreCase(projectKey)
        log.debug("\tProject:" + project.key + " (${project.id})")
        log.debug("Using:")
        Integer portalId = serviceDeskManager.getServiceDeskForProject(project).id
        log.debug("\tPortal ID:" + portalId)


        RequestType requestType = requestTypeService.getRequestTypes(serviceUser, requestTypeService.newQueryBuilder().serviceDesk(portalId).build()).find { it.name == requestTypeName }
        def newRequestType = requestTypeField.getCustomFieldType().getSingularObjectFromString(projectKey.toLowerCase() + "/" + requestType.key)
        log.debug("\tDetermined value to be!:" + newRequestType.toString())

        return newRequestType

    }
    /**
     * Get the RequestType object of an issue
     * @param issue The issue to get the request type from
     * @return The request type or null
     */
    RequestType getIssueRequestType(Issue issue) {

        ArrayList<RequestType> requestTypes = []


        try {
            requestTypes = requestTypeService.getRequestTypes(serviceUser, requestTypeService.newQueryBuilder().issue(issue.id).build()).results

        }catch(Exception ex) {

            if (ex.message == "The Service Desk you are trying to view does not exist.") {
                log.warn("Could not get request type for issue $issue as it does not belong to a ServiceDesk project")
            }else if (ex.message == "The request type you are trying to view does not exist."){
                log.warn("Could not get request type for issue $issue as it does not have one")
            }else {
                log.warn("MESS:" + ex.message + ":END")
                throw ex
            }

        }

        if (requestTypes.isEmpty()) {
            return null
        } else {
            return requestTypes.first()
        }

    }

    /**
     * A method for creating issue links between two issues
     * @param sourceIssue This issue will get the "Outward Link"
     * @param destinationIssue This issue will get the "Inward Link"
     * @param issueLinkTypeName The name of the issue link type, ex: Blocks, Cloners, Duplicate
     * @return the created IssueLink
     */
    IssueLink createIssueLink(Issue sourceIssue, Issue destinationIssue, String issueLinkTypeName) {

        log.info("Will create an issue link ($issueLinkTypeName) between the Source Issue: ${sourceIssue.key} and Destination Issue: ${destinationIssue.key}")

        IssueLinkType issueLinkType = issueLinkService.getIssueLinkTypes().find { it.name == issueLinkTypeName }

        if (issueLinkType == null) {
            log.error("Could not find an Issue link type with name:" + issueLinkTypeName)
            log.debug("The Available Issue link types are:" + issueLinkService.getIssueLinkTypes().name.join(","))
            throw new InputMismatchException("Could not find an Issue link type with name:" + issueLinkTypeName)
        }

        log.debug("\tDetermined issueLinkType ID to be:" + issueLinkType.id)
        log.debug("\tThe Source Issue (${sourceIssue.key}) will get the Outward Link Description:" + issueLinkType.outward)
        log.debug("\tThe Destination Issue (${destinationIssue.key}) will get the Inward Link Description:" + issueLinkType.inward)

        issueLinkManager.createIssueLink(sourceIssue.id, destinationIssue.id, issueLinkType.id, 1 as Long, this.serviceUser)

        IssueLink theNewIssueLink = issueLinkManager.getIssueLink(sourceIssue.id, destinationIssue.id, issueLinkType.id)

        if (theNewIssueLink == null) {
            throw new NullPointerException("There was an error creating an an issue link ($issueLinkTypeName) between the Source Issue: ${sourceIssue.key} and Destination Issue: ${destinationIssue.key}")
        }

        log.info("\tIssue link successfully created")
        return theNewIssueLink


    }

    /**
     *  A method for creating new Issue<br>
     *  Example:<br>
     *  createIssue([projectKey: "JIP", issueTypeName: "IT Help", summary: "This is the summary"], ["customfield_10303": "UTS-67, UTS-132"] )<br>
     *
     * @param issueParameters The basic parameters of an issue
     *  <ul>
     *      <li><b>Must contain:</b> projectKey, issueTypeName, summary</li>
     *      <li><b>May contain:</b> description, priority</li>
     *  </ul>
     * @param customfieldValues a map where the key is the ID of a field and the map value is the value that is to be set in that field. For example:
     * <ul>
     *     <li>["customfield_10303": "UTS-67, UTS-132"]</li>
     *     <li>[10303: ["UTS-67", "UTS-132"]</li>
     *  </ul>
     * @return the created Issue
     *
     *
     */
    Issue createIssue(Map issueParameters, Map customfieldValues) {

        MutableIssue newIssue

        log.info("Will create new Issue with the following input issueParameters:")
        issueParameters.each { log.info(it.key + ":" + it.value) }

        IssueInputParameters issueInputParameters = prepareIssueInput(issueParameters, customfieldValues)
        IssueService.CreateValidationResult createValidationResult = issueService.validateCreate(serviceUser, issueInputParameters)

        if (createValidationResult.isValid()) {
            log.debug("\tThe issue issueParameters appears valid, will now create issue")
            IssueService.IssueResult issueResult = issueService.create(serviceUser, createValidationResult)

            if (issueResult.isValid()) {

                newIssue = issueResult.issue

                log.debug("\tSuccessfully created issue:" + newIssue.key)

                return newIssue

            } else {

                log.error("There was an error when creating the issue")
                return null
            }

        } else {

            log.error("There was an error when validating the input issueParameters")
            createValidationResult.errorCollection.each {
                log.debug(it.toString())
            }
            return null

        }

    }



    /**
     *  A method for creating new Service Desk Requests<br>
     *  Example:<br>
     *  createServiceDeskRequest("IT Help", [projectKey: "JIP", issueTypeName: "IT Help", summary: "This is the summary"], ["customfield_10303": "UTS-67, UTS-132"] )<br>
     *
     *
     *
     * @param requestName Name of the Service Desk Request you want to create
     * @param issueParameters The basic parameters of an issue
     *  <ul>
     *      <li><b>Must contain:</b> projectKey, issueTypeName, summary</li>
     *      <li><b>May contain:</b> description, priority</li>
     *  </ul>
     * @param customfieldValues a map where the key is the ID of a field and the map value is the value that is to be set in that field. For example:
     * <ul>
     *     <li>["customfield_10303": "UTS-67, UTS-132"]</li>
     *     <li>[10303: ["UTS-67", "UTS-132"]</li>
     *  </ul>
     * @return the created Issue
     *
     *
     */
    Issue createServiceDeskRequest(String requestName, Map issueParameters, Map customfieldValues) {

        MutableIssue newIssue = createIssue(issueParameters, customfieldValues)
        assert newIssue : "Error creating ServiceDeskRequest"
        CustomField requestTypeField = customFieldManager.getCustomFieldObjectsByName("Customer Request Type").first()

        log.debug("\tSetting request type to:" + requestName)
        def requestType = getRequestTypeFieldValue(issueParameters.projectKey as String, requestName)
        newIssue.setCustomFieldValue(requestTypeField, requestType)
        newIssue = issueManager.updateIssue(serviceUser, newIssue, EventDispatchOption.ISSUE_UPDATED, false) as MutableIssue

        return newIssue

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


    /**
     * Creates an application link to a remote Bitbucket instance with "OAuth (impersonation)"
     * @param linkName Name of the new application link
     * @param linkIsPrimary Should this the primary link of this type
     * @param remoteUrl Url of the remote application, including protocol and port
     * @param remoteAdminUser An admin username on the remote application
     * @param remoteAdminPw Corresponding password
     * @return Thre created application link
     */
    ApplicationLink createApplicationLinkToBitbucket(String linkName, boolean linkIsPrimary = true, String remoteUrl, String remoteAdminUser, String remoteAdminPw ) {
        return createApplicationLink(BitbucketApplicationType, linkName, linkIsPrimary, remoteUrl, remoteAdminUser, remoteAdminPw)
    }

    ArrayList<ApplicationLink> getApplicationLinks() {
        return appLinkService.getApplicationLinks().toList()
    }

    ApplicationLink getApplicationLink(String nameOrUrl) {

        return getApplicationLinks().find {it.name == nameOrUrl || it.displayUrl.toString() == nameOrUrl || it.rpcUrl.toString() == nameOrUrl} as ApplicationLink

    }


    boolean deleteApplicationLink(ApplicationLink appLink) {
        appLinkService.deleteReciprocatedApplicationLink(appLink)
        appLinkService.deleteApplicationLink(appLink)

        return appLinkService.getApplicationLink(appLink.id) == null
    }



}
