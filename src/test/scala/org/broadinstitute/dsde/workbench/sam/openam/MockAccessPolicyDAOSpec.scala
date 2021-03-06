package org.broadinstitute.dsde.workbench.sam.openam

import java.net.URI

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.unboundid.ldap.sdk.{LDAPConnection, LDAPConnectionPool}
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.sam.TestSupport
import org.broadinstitute.dsde.workbench.sam.api.CreateWorkbenchUser
import org.broadinstitute.dsde.workbench.sam.config.DirectoryConfig
import org.broadinstitute.dsde.workbench.sam.directory._
import org.broadinstitute.dsde.workbench.sam.model._
import org.broadinstitute.dsde.workbench.sam.schema.JndiSchemaDAO
import org.broadinstitute.dsde.workbench.sam.service._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.reflectiveCalls

/**
  * Created by dvoet on 6/26/17.
  */
class MockAccessPolicyDAOSpec extends FlatSpec with Matchers with TestSupport with BeforeAndAfter with BeforeAndAfterAll {
  val directoryConfig: DirectoryConfig = TestSupport.appConfig.directoryConfig
  val schemaLockConfig = TestSupport.appConfig.schemaLockConfig
  val schemaDao = new JndiSchemaDAO(directoryConfig, schemaLockConfig)
  private val dummyUserInfo = UserInfo(OAuth2BearerToken("token"), WorkbenchUserId("userid"), WorkbenchEmail("user@company.com"), 0)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    runAndWait(schemaDao.init())
  }

  before {
    runAndWait(schemaDao.clearDatabase())
    runAndWait(schemaDao.createOrgUnits())
  }

  def sharedFixtures = new {
    val groups: mutable.Map[WorkbenchGroupIdentity, WorkbenchGroup] = new TrieMap()
    val accessPolicyNames = Set(ManagedGroupService.adminPolicyName, ManagedGroupService.memberPolicyName, ManagedGroupService.adminNotifierPolicyName)
    val policyActions: Set[ResourceAction] = accessPolicyNames.flatMap(policyName => Set(SamResourceActions.sharePolicy(policyName), SamResourceActions.readPolicy(policyName)))
    val resourceActions: Set[ResourceAction] = Set(ResourceAction("delete"), SamResourceActions.notifyAdmins) union policyActions
    val resourceActionPatterns: Set[ResourceActionPattern] = resourceActions.map(action => ResourceActionPattern(action.value, "", false))
    val defaultOwnerRole = ResourceRole(ManagedGroupService.adminRoleName, resourceActions)
    val defaultMemberRole = ResourceRole(ManagedGroupService.memberRoleName, Set.empty)
    val defaultAdminNotifierRole = ResourceRole(ManagedGroupService.adminNotifierRoleName, Set(ResourceAction("notify_admins")))
    val defaultRoles = Set(defaultOwnerRole, defaultMemberRole, defaultAdminNotifierRole)
    val managedGroupResourceType = ResourceType(ManagedGroupService.managedGroupTypeName, resourceActionPatterns, defaultRoles, ManagedGroupService.adminRoleName)
    val resourceTypes = Map(managedGroupResourceType.name -> managedGroupResourceType)
    val emailDomain = "example.com"
  }

  def jndiServicesFixture = new {
    val shared = sharedFixtures
    val dirURI = new URI(directoryConfig.directoryUrl)
    val connectionPool = new LDAPConnectionPool(new LDAPConnection(dirURI.getHost, dirURI.getPort, directoryConfig.user, directoryConfig.password), directoryConfig.connectionPoolSize)
    val ldapPolicyDao = new LdapAccessPolicyDAO(connectionPool, directoryConfig, TestSupport.blockingEc, TestSupport.testMemberOfCache, TestSupport.testResourceCache)
    val ldapDirDao = new LdapDirectoryDAO(connectionPool, directoryConfig, TestSupport.blockingEc, TestSupport.testMemberOfCache)
    val allUsersGroup: WorkbenchGroup = TestSupport.runAndWait(NoExtensions.getOrCreateAllUsersGroup(ldapDirDao))

    val policyEvaluatorService = PolicyEvaluatorService(shared.emailDomain, shared.resourceTypes, ldapPolicyDao)
    val resourceService = new ResourceService(shared.resourceTypes, policyEvaluatorService, ldapPolicyDao, ldapDirDao, NoExtensions, shared.emailDomain)
    val userService = new UserService(ldapDirDao, NoExtensions)
    val managedGroupService = new ManagedGroupService(resourceService, policyEvaluatorService, shared.resourceTypes, ldapPolicyDao, ldapDirDao, NoExtensions, shared.emailDomain)
    shared.resourceTypes foreach {case (_, resourceType) => resourceService.createResourceType(resourceType).unsafeRunSync() }
  }

  def mockServicesFixture = new {
    val shared = sharedFixtures
    val mockDirDao = new MockDirectoryDAO(shared.groups)
    val mockPolicyDAO = new MockAccessPolicyDAO(shared.groups)
    val allUsersGroup: WorkbenchGroup = TestSupport.runAndWait(NoExtensions.getOrCreateAllUsersGroup(mockDirDao))

    val policyEvaluatorService = PolicyEvaluatorService(shared.emailDomain, shared.resourceTypes, mockPolicyDAO)
    val resourceService = new ResourceService(shared.resourceTypes, policyEvaluatorService, mockPolicyDAO, mockDirDao, NoExtensions, shared.emailDomain)
    val userService = new UserService(mockDirDao, NoExtensions)
    val managedGroupService = new ManagedGroupService(resourceService, policyEvaluatorService, shared.resourceTypes, mockPolicyDAO, mockDirDao, NoExtensions, shared.emailDomain)
  }

  "JndiAccessPolicyDao and MockAccessPolicyDao" should "return the same results for the same methods" in {
    val jndi = jndiServicesFixture
    val mock = mockServicesFixture

    val dummyUser = CreateWorkbenchUser(dummyUserInfo.userId, GoogleSubjectId(dummyUserInfo.userId.value), dummyUserInfo.userEmail)
    runAndWait(jndi.userService.createUser(dummyUser))
    runAndWait(mock.userService.createUser(dummyUser))

    val groupName = "fooGroup"

    val intendedResource = Resource(ManagedGroupService.managedGroupTypeName, ResourceId(groupName), Set.empty)
    runAndWait(jndi.managedGroupService.createManagedGroup(ResourceId(groupName), dummyUserInfo)) shouldEqual intendedResource
    runAndWait(mock.managedGroupService.createManagedGroup(ResourceId(groupName), dummyUserInfo)) shouldEqual intendedResource

    val expectedGroups = Set(ResourceIdAndPolicyName(ResourceId(groupName), ManagedGroupService.adminPolicyName))
    jndi.managedGroupService.listGroups(dummyUserInfo.userId).unsafeRunSync().map(ripn => ResourceIdAndPolicyName(ripn.groupName, ripn.role)) shouldEqual expectedGroups
    mock.managedGroupService.listGroups(dummyUserInfo.userId).unsafeRunSync().map(ripn => ResourceIdAndPolicyName(ripn.groupName, ripn.role)) shouldEqual expectedGroups
  }
}
