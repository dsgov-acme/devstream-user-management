package io.nuvalence.user.management.api.service.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nuvalence.auth.access.AuthorizationHandler;
import io.nuvalence.user.management.api.service.entity.RoleEntity;
import io.nuvalence.user.management.api.service.entity.UserEntity;
import io.nuvalence.user.management.api.service.entity.UserPreferenceEntity;
import io.nuvalence.user.management.api.service.enums.UserType;
import io.nuvalence.user.management.api.service.generated.models.UserCreationRequest;
import io.nuvalence.user.management.api.service.generated.models.UserPreferenceDTO;
import io.nuvalence.user.management.api.service.generated.models.UserUpdateRequest;
import io.nuvalence.user.management.api.service.mapper.UserPreferenceEntityMapper;
import io.nuvalence.user.management.api.service.repository.UserRepository;
import io.nuvalence.user.management.api.service.service.UserPreferenceService;
import io.nuvalence.user.management.api.service.service.UserSearchCriteria;
import io.nuvalence.user.management.api.service.service.UserService;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@SuppressWarnings({"PMD"})
class UsersApiDelegateImplTest {
    @Autowired private MockMvc mockMvc;

    @MockBean private UserRepository userRepository;

    @MockBean private UserService userService;
    @MockBean private UserPreferenceService userPreferenceService;

    @MockBean private AuthorizationHandler authorizationHandler;

    @BeforeEach
    void setup() {
        when(authorizationHandler.isAllowed(any(), (Class<?>) any())).thenReturn(true);
        when(authorizationHandler.isAllowed(any(), (String) any())).thenReturn(true);
        when(authorizationHandler.isAllowedForInstance(any(), any())).thenReturn(true);
        when(authorizationHandler.getAuthFilter(any(), any())).thenReturn(element -> true);
    }

    @Test
    @WithMockUser
    void addUser() throws Exception {
        UserCreationRequest user = createNewUserModel();
        when(userService.createUser(any())).thenReturn(createMockUser());
        final String postBody = new ObjectMapper().writeValueAsString(user);
        mockMvc.perform(
                        post("/api/v1/users")
                                .content(postBody)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void addUser_Forbidden() throws Exception {
        UserCreationRequest user = createNewUserModel();
        when(authorizationHandler.isAllowed("create", UserEntity.class)).thenReturn(false);

        final String postBody = new ObjectMapper().writeValueAsString(user);

        mockMvc.perform(
                        post("/api/v1/users")
                                .content(postBody)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void addUser_DuplicateExternalUser() throws Exception {
        UserCreationRequest user = createNewUserModel();
        when(userService.createUser(any()))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "",
                                new ConstraintViolationException(
                                        "", null, "user_table_identity_provider_external_id_key")));
        when(userService.isDuplicateExternalUserException(any())).thenReturn(true);

        final String postBody = new ObjectMapper().writeValueAsString(user);

        mockMvc.perform(
                        post("/api/v1/users")
                                .content(postBody)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.messages[0]")
                                .value(
                                        "A user already exists with this identityProvider and"
                                                + " externalId"));
    }

    @Test
    @WithMockUser
    void deleteUserById() throws Exception {
        UserEntity userEntity = createMockUser();
        when(userRepository.findById(userEntity.getId())).thenReturn(Optional.of(userEntity));

        mockMvc.perform(delete("/api/v1/users/" + userEntity.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getUserById_NotFound() throws Exception {
        UUID userId = UUID.randomUUID();

        when(userService.getUserByIdLoaded(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/" + userId)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getUserByIdWithNullProperties() throws Exception {
        UserEntity userEntity = createMockUser();
        userEntity.setFirstName(null);
        userEntity.setMiddleName(null);
        userEntity.setLastName(null);
        userEntity.setPhoneNumber(null);

        when(userService.getUserByIdLoaded(userEntity.getId())).thenReturn(Optional.of(userEntity));
        mockMvc.perform(get("/api/v1/users/" + userEntity.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value(nullValue()));
    }

    @Test
    @WithMockUser
    void updateUserById() throws Exception {
        UserEntity userEntity = createMockUser();
        when(userService.getUserByIdLoaded(userEntity.getId())).thenReturn(Optional.of(userEntity));
        when(authorizationHandler.isAllowedForInstance("update", userEntity)).thenReturn(true);
        when(userService.updateUserById(userEntity.getId(), new UserUpdateRequest()))
                .thenReturn(userEntity);

        mockMvc.perform(
                        put("/api/v1/users/" + userEntity.getId().toString())
                                .content("{}")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @WithMockUser
    void updateUserByIdWithNullableProperties() throws Exception {
        UserEntity userEntity = createMockUser();
        userEntity.setFirstName(null);
        userEntity.setMiddleName(null);
        userEntity.setLastName(null);
        userEntity.setPhoneNumber(null);
        when(userService.getUserByIdLoaded(userEntity.getId())).thenReturn(Optional.of(userEntity));
        when(authorizationHandler.isAllowedForInstance("update", userEntity)).thenReturn(true);
        when(userService.updateUserById(userEntity.getId(), new UserUpdateRequest()))
                .thenReturn(userEntity);

        mockMvc.perform(
                        put("/api/v1/users/" + userEntity.getId().toString())
                                .content("{}")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.firstName").value(nullValue()));
    }

    private UserCreationRequest createNewUserModel() {
        UserCreationRequest user = new UserCreationRequest();
        user.setIdentityProvider("https://securetoken.google.com/my-project");
        user.setExternalId("SMGjTO5n3sZFVIi5IzpW2pI8vjf1");
        user.firstName("Rusty");
        user.middleName("Popins");
        user.lastName("Smith");
        user.phoneNumber("555-555-5555");
        user.setEmail("Rdawg@gmail.com");
        user.setUserType("public");
        return user;
    }

    private UserEntity createMockUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setFirstName("John");
        user.setMiddleName("Locke");
        user.setLastName("Doe");
        user.setEmail("Rdawg@gmail.com");
        user.setIdentityProvider("https://securetoken.google.com/my-project");
        user.setExternalId("SMGjTO5n3sZFVIi5IzpW2pI8vjf1");
        user.setPhoneNumber("555-555-5555");
        user.setUserType(UserType.PUBLIC);
        user.setDeleted(false);
        user.setDeletedOn(null);
        return user;
    }

    // List all users tests
    @Test
    @WithMockUser
    void getUserListWithoutParams() throws Exception {
        List<UserEntity> users = Arrays.asList(createMockUser(), createMockUser());
        Page<UserEntity> userPage = new PageImpl<>(users);

        when(userService.getUsersBySearchCriteria(
                        eq(UserSearchCriteria.builder().build()), any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(users.size()));
    }

    @Test
    @WithMockUser
    void getUserListByEmail() throws Exception {
        UserEntity user = createMockUser();
        List<UserEntity> users = Collections.singletonList(user);
        Page<UserEntity> userPage = new PageImpl<>(users);
        when(userService.getUsersBySearchCriteria(
                        eq(UserSearchCriteria.builder().email(user.getEmail()).build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(get("/api/v1/users").param("email", user.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.users[0].email").value(user.getEmail()));
    }

    @Test
    @WithMockUser
    void getUserListByExternalId() throws Exception {
        UserEntity user = createMockUser();
        List<UserEntity> users = Collections.singletonList(user);
        Page<UserEntity> userPage = new PageImpl<>(users);

        when(userService.getUsersBySearchCriteria(
                        eq(UserSearchCriteria.builder().externalId(user.getExternalId()).build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(get("/api/v1/users").param("externalId", user.getExternalId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.users[0].externalId").value(user.getExternalId()));
    }

    @Test
    @WithMockUser
    void getUserListByRole() throws Exception {
        UserEntity user = createMockUser();
        List<UserEntity> users = Collections.singletonList(user);
        Page<UserEntity> userPage = new PageImpl<>(users);
        UUID roleId = UUID.randomUUID();
        UUID missedRoleId = UUID.randomUUID();

        when(userService.getUsersBySearchCriteria(
                        eq(
                                UserSearchCriteria.builder()
                                        .roleIds(
                                                List.of(roleId.toString(), missedRoleId.toString()))
                                        .build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(get("/api/v1/users").param("roleIds", roleId + "," + missedRoleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()));
    }

    @Test
    @WithMockUser
    void getUserListByEmailAndExternalId() throws Exception {
        UserEntity user = createMockUser();
        List<UserEntity> users = Collections.singletonList(user);
        Page<UserEntity> userPage = new PageImpl<>(users);

        when(userService.getUsersBySearchCriteria(
                        eq(
                                UserSearchCriteria.builder()
                                        .email(user.getEmail())
                                        .externalId(user.getExternalId())
                                        .build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(
                        get("/api/v1/users")
                                .param("email", user.getEmail())
                                .param("externalId", user.getExternalId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.users[0].email").value(user.getEmail()))
                .andExpect(jsonPath("$.users[0].externalId").value(user.getExternalId()));
    }

    @Test
    @WithMockUser
    void getUserListByEmailAndRole() throws Exception {
        UserEntity user = createMockUser();
        List<UserEntity> users = Collections.singletonList(user);
        Page<UserEntity> userPage = new PageImpl<>(users);
        UUID roleId = UUID.randomUUID();
        UUID missedRoleId = UUID.randomUUID();

        when(userService.getUsersBySearchCriteria(
                        eq(
                                UserSearchCriteria.builder()
                                        .email(user.getEmail())
                                        .roleIds(
                                                List.of(roleId.toString(), missedRoleId.toString()))
                                        .build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(
                        get("/api/v1/users")
                                .param("email", user.getEmail())
                                .param("roleIds", roleId + "," + missedRoleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.users[0].email").value(user.getEmail()));
    }

    @Test
    @WithMockUser
    void getUserListByRoleAndExternalId() throws Exception {
        UserEntity user = createMockUser();
        List<UserEntity> users = Collections.singletonList(user);
        Page<UserEntity> userPage = new PageImpl<>(users);
        UUID roleId = UUID.randomUUID();

        when(userService.getUsersBySearchCriteria(
                        eq(
                                UserSearchCriteria.builder()
                                        .externalId(user.getExternalId())
                                        .roleIds(List.of(roleId.toString()))
                                        .build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(
                        get("/api/v1/users")
                                .param("roleIds", roleId.toString())
                                .param("externalId", user.getExternalId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].externalId").value(user.getExternalId()));
    }

    @Test
    @WithMockUser
    void getUserListByEmailRoleAndExternalId() throws Exception {
        UserEntity user = createMockUser();
        List<UserEntity> users = Collections.singletonList(user);
        Page<UserEntity> userPage = new PageImpl<>(users);
        UUID roleId1 = UUID.randomUUID();
        UUID roleId2 = UUID.randomUUID();
        String roleIdsString = roleId1 + "," + roleId2;

        when(userService.getUsersBySearchCriteria(
                        eq(
                                UserSearchCriteria.builder()
                                        .email(user.getEmail())
                                        .externalId(user.getExternalId())
                                        .roleIds(List.of(roleId1.toString(), roleId2.toString()))
                                        .build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(
                        get("/api/v1/users")
                                .param("email", user.getEmail())
                                .param("roleIds", roleIdsString)
                                .param("externalId", user.getExternalId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.users[0].email").value(user.getEmail()))
                .andExpect(jsonPath("$.users[0].externalId").value(user.getExternalId()));
    }

    @Test
    @WithMockUser
    void getUserListPaginationTest() throws Exception {
        UserEntity userOne = createMockUser();
        UserEntity userTwo = createMockUser();
        UserEntity userThree = createMockUser();

        int currentPage = 0;
        int pageSize = 3;
        int totalUsers = 5;

        Pageable userPageable = PageRequest.of(currentPage, pageSize);

        List<UserEntity> users = List.of(userOne, userTwo, userThree);
        Page<UserEntity> userPage = new PageImpl<>(users, userPageable, totalUsers);

        when(userService.getUsersBySearchCriteria(
                        eq(UserSearchCriteria.builder().build()), any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(
                        get("/api/v1/users")
                                .queryParam("page", String.valueOf(currentPage))
                                .queryParam("size", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.pagingMetadata.totalCount").value(totalUsers))
                .andExpect(jsonPath("$.pagingMetadata.pageNumber").value(currentPage))
                .andExpect(jsonPath("$.pagingMetadata.pageSize").value(pageSize));
    }

    @Test
    @WithMockUser
    void pageSizeLimitTest() throws Exception {
        int currentPage = 0;
        int pageSize = 201; // Limit is 200
        int totalUsers = 1;

        UserEntity user = createMockUser();

        Pageable userPageable = PageRequest.of(currentPage, pageSize);

        List<UserEntity> users = List.of(user);
        Page<UserEntity> userPage = new PageImpl<>(users, userPageable, totalUsers);

        when(userService.getUserList(any(Pageable.class))).thenReturn(userPage);

        mockMvc.perform(
                        get("/api/v1/users")
                                .queryParam("pageNumber", String.valueOf(currentPage))
                                .queryParam("pageSize", String.valueOf(pageSize)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser
    void testListUserByRoleName() throws Exception {
        UserEntity userOne = createMockUser();
        UserEntity userTwo = createMockUser();
        String roleNameOne = "ROLE_1";
        String roleNameTwo = "ROLE_2";
        UUID roleIdOne = UUID.randomUUID();
        UUID roleIdTwo = UUID.randomUUID();
        RoleEntity roleOne =
                new RoleEntity(
                        roleIdOne,
                        roleNameOne,
                        "description",
                        Collections.singletonList(userOne),
                        List.of());
        RoleEntity roleTwo =
                new RoleEntity(
                        roleIdTwo,
                        roleNameTwo,
                        "description",
                        Collections.singletonList(userTwo),
                        List.of());
        userOne.setRoles(List.of(roleOne));
        userTwo.setRoles(List.of(roleTwo));

        List<UserEntity> users = List.of(userOne, userTwo);
        Page<UserEntity> userPage = new PageImpl<>(users);

        String roleNamesString = roleNameOne + "," + roleNameTwo;

        when(userService.getUsersBySearchCriteria(
                        eq(
                                UserSearchCriteria.builder()
                                        .roleNames(List.of(roleNameOne, roleNameTwo))
                                        .build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(get("/api/v1/users").param("roleNames", roleNamesString))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.users[0].assignedRoles[0].id").value(roleIdOne.toString()))
                .andExpect(jsonPath("$.users[1].assignedRoles[0].id").value(roleIdTwo.toString()));
    }

    @Test
    @WithMockUser
    void testListUserByRoleNameWithEmailAndExternalId() throws Exception {
        UserEntity userOne = createMockUser();
        UserEntity userTwo = createMockUser();
        String roleNameOne = "ROLE_1";
        String roleNameTwo = "ROLE_2";
        UUID roleIdOne = UUID.randomUUID();
        UUID roleIdTwo = UUID.randomUUID();
        RoleEntity roleOne =
                new RoleEntity(
                        roleIdOne,
                        roleNameOne,
                        "description",
                        Collections.singletonList(userOne),
                        List.of());
        RoleEntity roleTwo =
                new RoleEntity(
                        roleIdTwo,
                        roleNameTwo,
                        "description",
                        Collections.singletonList(userTwo),
                        List.of());
        userOne.setRoles(List.of(roleOne));
        userTwo.setRoles(List.of(roleTwo));

        List<UserEntity> users = List.of(userOne, userTwo);
        Page<UserEntity> userPage = new PageImpl<>(users);

        String roleNamesString = roleNameOne + "," + roleNameTwo;

        when(userService.getUsersBySearchCriteria(
                        eq(
                                UserSearchCriteria.builder()
                                        .email(userOne.getEmail())
                                        .externalId(userOne.getExternalId())
                                        .roleNames(List.of(roleNameOne, roleNameTwo))
                                        .build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(
                        get("/api/v1/users")
                                .param("roleNames", roleNamesString)
                                .param("email", userOne.getEmail())
                                .param("externalId", userOne.getExternalId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.users[0].assignedRoles[0].id").value(roleIdOne.toString()))
                .andExpect(jsonPath("$.users[1].assignedRoles[0].id").value(roleIdTwo.toString()))
                .andExpect(jsonPath("$.users[0].email").value(userOne.getEmail()))
                .andExpect(jsonPath("$.users[0].externalId").value(userOne.getExternalId()));
    }

    @Test
    @WithMockUser
    void testListUserByRoleNameWithEmail() throws Exception {
        UserEntity userOne = createMockUser();
        UserEntity userTwo = createMockUser();
        String roleNameOne = "ROLE_1";
        String roleNameTwo = "ROLE_2";
        UUID roleIdOne = UUID.randomUUID();
        UUID roleIdTwo = UUID.randomUUID();
        RoleEntity roleOne =
                new RoleEntity(
                        roleIdOne,
                        roleNameOne,
                        "description",
                        Collections.singletonList(userOne),
                        List.of());
        RoleEntity roleTwo =
                new RoleEntity(
                        roleIdTwo,
                        roleNameTwo,
                        "description",
                        Collections.singletonList(userTwo),
                        List.of());
        userOne.setRoles(List.of(roleOne));
        userTwo.setRoles(List.of(roleTwo));

        List<UserEntity> users = List.of(userOne, userTwo);
        Page<UserEntity> userPage = new PageImpl<>(users);

        String roleNamesString = roleNameOne + "," + roleNameTwo;

        when(userService.getUsersBySearchCriteria(
                        eq(
                                UserSearchCriteria.builder()
                                        .email(userOne.getEmail())
                                        .roleNames(List.of(roleNameOne, roleNameTwo))
                                        .build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(
                        get("/api/v1/users")
                                .param("roleNames", roleNamesString)
                                .param("email", userOne.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.users[0].assignedRoles[0].id").value(roleIdOne.toString()))
                .andExpect(jsonPath("$.users[1].assignedRoles[0].id").value(roleIdTwo.toString()))
                .andExpect(jsonPath("$.users[0].email").value(userOne.getEmail()));
    }

    @Test
    @WithMockUser
    void getUserPreferencesWithExistingPreferences() throws Exception {
        UserPreferenceDTO expectedPreferences = createUserPreferenceDTO("en", "sms");
        UserPreferenceEntity userPreferenceEntity = createUserPreference();
        when(userPreferenceService.getUserPreferences(userPreferenceEntity.getUser().getId()))
                .thenReturn(Optional.of(userPreferenceEntity));
        when(authorizationHandler.getAuthFilter("view", UserPreferenceEntity.class))
                .thenReturn(userPreference -> true); // Allow access
        UserPreferenceEntityMapper formConfigurationMapper =
                Mockito.mock(UserPreferenceEntityMapper.class);
        when(formConfigurationMapper.convertUserPreferenceEntityToUserModel(userPreferenceEntity))
                .thenReturn(expectedPreferences);

        mockMvc.perform(
                        get(
                                "/api/v1/users/{id}/preferences",
                                userPreferenceEntity.getUser().getId()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.preferredLanguage")
                                .value(expectedPreferences.getPreferredLanguage()))
                .andExpect(
                        jsonPath("$.preferredCommunicationMethod")
                                .value(expectedPreferences.getPreferredCommunicationMethod()));
    }

    @Test
    void getUserPreferencesWithNonExistingPreferences() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userPreferenceService.getUserPreferences(userId)).thenReturn(Optional.empty());

        when(authorizationHandler.getAuthFilter("view", UserPreferenceEntity.class))
                .thenReturn(userPreferenceEntity -> true); // Allow access

        mockMvc.perform(get("/api/v1/users/{id}/preferences", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdatePreferencesWithValidData() throws Exception {
        UserPreferenceDTO userPreferences = createUserPreferenceDTO("en", "sms");
        UserEntity user = createMockUser();
        when(userService.getUserById(user.getId())).thenReturn(Optional.of(user));
        when(authorizationHandler.isAllowedForInstance("update", user)).thenReturn(true);
        mockMvc.perform(
                        MockMvcRequestBuilders.put("/api/v1/users/{id}/preferences", user.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void testUpdatePreferencesWithNonExistingId() throws Exception {
        UserPreferenceDTO userPreferences = createUserPreferenceDTO("en", "sms");
        UserEntity user = createMockUser();
        when(userService.getUserById(user.getId())).thenReturn(Optional.empty());
        when(authorizationHandler.isAllowedForInstance("update", user)).thenReturn(true);
        mockMvc.perform(
                        MockMvcRequestBuilders.put("/api/v1/users/{id}/preferences", user.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testUpdatePreferencesWithNotPermissions() throws Exception {
        UserEntity user = createMockUser();
        when(userService.getUserById(user.getId())).thenReturn(Optional.of(user));
        when(authorizationHandler.isAllowedForInstance("update", user)).thenReturn(false);
        mockMvc.perform(
                        MockMvcRequestBuilders.put("/api/v1/users/{id}/preferences", user.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser
    void getUserListByincludeDeletedUsers() throws Exception {
        UserEntity user = createMockUser();
        UserEntity deletedUser = createMockUser();
        deletedUser.setDeleted(true);
        deletedUser.setDeletedOn(OffsetDateTime.now());

        List<UserEntity> users = List.of(user, deletedUser);
        Page<UserEntity> userPage = new PageImpl<>(users);

        when(userService.getUsersBySearchCriteria(
                        eq(UserSearchCriteria.builder().includeDeleted(true).build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(get("/api/v1/users").param("includeDeleted", String.valueOf(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(users.size()))
                .andExpect(jsonPath("$.users[1].deleted").value(true))
                .andExpect(
                        jsonPath("$.users[1].deletedOn")
                                .value(
                                        areStringDateAndOffsetDateTimeEqual(
                                                deletedUser.getDeletedOn())))
                .andExpect(jsonPath("$.users[0].deleted").value(false));
    }

    private Matcher<String> areStringDateAndOffsetDateTimeEqual(OffsetDateTime expectedTimestamp) {
        return new TypeSafeMatcher<String>() {
            @Override
            protected boolean matchesSafely(String actualTimestampStr) {
                DateTimeFormatter formatter =
                        new DateTimeFormatterBuilder()
                                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                .appendOffsetId()
                                .toFormatter();

                OffsetDateTime actualTimestamp =
                        OffsetDateTime.parse(actualTimestampStr, formatter);
                return actualTimestamp.equals(expectedTimestamp);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Expected and actual dates are different");
            }
        };
    }

    @Test
    @WithMockUser
    void getUserListByincludeDeletedUsersFalse() throws Exception {
        UserEntity user = createMockUser();
        UserEntity deletedUser = createMockUser();
        deletedUser.setDeleted(true);
        List<UserEntity> users = List.of(user);
        Page<UserEntity> userPage = new PageImpl<>(users);

        when(userService.getUsersBySearchCriteria(
                        eq(UserSearchCriteria.builder().includeDeleted(false).build()),
                        any(Pageable.class)))
                .thenReturn(userPage);

        mockMvc.perform(get("/api/v1/users").param("includeDeleted", String.valueOf(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(1));
    }

    private UserPreferenceEntity createUserPreference() {
        UserPreferenceEntity userPreference = new UserPreferenceEntity();
        UserEntity user = createMockUser();
        userPreference.setUser(user);
        userPreference.setPreferredLanguage("en");
        userPreference.setPreferredCommunicationMethod("sms");
        return userPreference;
    }

    private UserPreferenceDTO createUserPreferenceDTO(String language, String communicationMethod) {
        UserPreferenceDTO userPreferenceDTO = new UserPreferenceDTO();
        userPreferenceDTO.setPreferredCommunicationMethod(communicationMethod);
        userPreferenceDTO.setPreferredLanguage(language);
        return userPreferenceDTO;
    }
}
