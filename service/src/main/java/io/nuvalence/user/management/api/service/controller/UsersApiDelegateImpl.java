package io.nuvalence.user.management.api.service.controller;

import io.nuvalence.auth.access.AuthorizationHandler;
import io.nuvalence.user.management.api.service.config.exception.ResourceNotFoundException;
import io.nuvalence.user.management.api.service.entity.UserEntity;
import io.nuvalence.user.management.api.service.entity.UserPreferenceEntity;
import io.nuvalence.user.management.api.service.enums.SortOrder;
import io.nuvalence.user.management.api.service.generated.controllers.UsersApiDelegate;
import io.nuvalence.user.management.api.service.generated.models.UserCreationRequest;
import io.nuvalence.user.management.api.service.generated.models.UserDTO;
import io.nuvalence.user.management.api.service.generated.models.UserPageDTO;
import io.nuvalence.user.management.api.service.generated.models.UserPreferenceDTO;
import io.nuvalence.user.management.api.service.generated.models.UserUpdateRequest;
import io.nuvalence.user.management.api.service.mapper.MapperUtils;
import io.nuvalence.user.management.api.service.mapper.PagingMetadataMapper;
import io.nuvalence.user.management.api.service.mapper.UserEntityMapper;
import io.nuvalence.user.management.api.service.mapper.UserPreferenceEntityMapper;
import io.nuvalence.user.management.api.service.service.UserPreferenceService;
import io.nuvalence.user.management.api.service.service.UserSearchCriteria;
import io.nuvalence.user.management.api.service.service.UserService;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for cloud function user actions.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
@Slf4j
class UsersApiDelegateImpl implements UsersApiDelegate {

    private static final String USER_NOT_FOUND_EXCEPTION_MESSAGE = "User not found!";

    private static final String VIEW_AUTHORIZATION = "view";
    private final UserService userService;

    private final AuthorizationHandler authorizationHandler;

    private final PagingMetadataMapper pagingMetadataMapper;

    private final UserPreferenceService userPreferenceService;

    @Override
    public ResponseEntity<UserDTO> addUser(UserCreationRequest body) {
        if (!authorizationHandler.isAllowed("create", UserEntity.class)) {
            throw new AccessDeniedException("You do not have permission to create this resource.");
        }

        final UserEntity user =
                UserEntityMapper.INSTANCE.convertUserCreationRequestToUserEntity(body);
        try {
            final UserEntity updated = userService.createUser(user);
            return ResponseEntity.ok(
                    UserEntityMapper.INSTANCE.convertUserEntityToUserModel(updated));
        } catch (RuntimeException e) {
            if (userService.isDuplicateExternalUserException(e)) {
                throw new ConstraintViolationException(
                        "A user already exists with this identityProvider and externalId",
                        Collections.emptySet());
            }

            throw e;
        }
    }

    @Override
    @Transactional
    public ResponseEntity<UserPageDTO> getUserList(
            List<String> roleIds,
            String email,
            String externalId,
            String name,
            Integer pageNumber,
            Integer pageSize,
            String sortOrder,
            String sortBy,
            List<String> roleNames,
            String identityProvider,
            String userType,
            Boolean includeDeleted) {
        final Pageable pageable =
                PageRequest.of(
                        pageNumber,
                        pageSize,
                        (SortOrder.DESC.toString().equals(sortBy))
                                ? Sort.by(sortBy).descending()
                                : Sort.by(sortBy).ascending());

        final UserSearchCriteria searchCriteria =
                UserSearchCriteria.builder()
                        .email(email)
                        .externalId(externalId)
                        .identityProvider(identityProvider)
                        .userType(userType)
                        .name(name)
                        .includeDeleted(includeDeleted)
                        .roleIds(roleIds)
                        .roleNames(roleNames)
                        .build();

        final Page<UserEntity> userPage =
                userService.getUsersBySearchCriteria(searchCriteria, pageable);

        final List<UserDTO> users =
                userPage.stream()
                        .filter(
                                authorizationHandler.getAuthFilter(
                                        VIEW_AUTHORIZATION, UserEntity.class))
                        .map(this::mapUserEntity)
                        .collect(Collectors.toList());

        final UserPageDTO userPageDTO = new UserPageDTO();
        userPageDTO.setUsers(users);
        userPageDTO.setPagingMetadata(pagingMetadataMapper.toPagingMetadata(userPage));

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(userPageDTO);
    }

    @Override
    public ResponseEntity<Void> deleteUserById(UUID id) {
        if (!authorizationHandler.isAllowed("delete", UserEntity.class)) {
            throw new AccessDeniedException("You do not have permission to delete this resource.");
        }
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<UserDTO> getUserById(UUID id) {
        UserEntity user =
                userService
                        .getUserByIdLoaded(id)
                        .filter(
                                userEntity ->
                                        authorizationHandler.isAllowedForInstance(
                                                VIEW_AUTHORIZATION, userEntity))
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                USER_NOT_FOUND_EXCEPTION_MESSAGE));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapUserEntity(user));
    }

    @Override
    public ResponseEntity<UserDTO> updateUserById(UUID id, UserUpdateRequest body) {
        Optional<UserEntity> userEntityOptional = userService.getUserByIdLoaded(id);

        if (userEntityOptional.isEmpty()) {
            throw new ResourceNotFoundException(USER_NOT_FOUND_EXCEPTION_MESSAGE);
        }
        if (!authorizationHandler.isAllowedForInstance("update", userEntityOptional.get())) {
            throw new AccessDeniedException("You do not have permission to update this resource.");
        }

        UserEntity user = userService.updateUserById(id, body);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapUserEntity(user));
    }

    @Override
    public ResponseEntity<UserPreferenceDTO> getUserPreferences(UUID id) {

        UserPreferenceDTO preferences =
                userPreferenceService
                        .getUserPreferences(id)
                        .filter(
                                authorizationHandler.getAuthFilter(
                                        VIEW_AUTHORIZATION, UserPreferenceEntity.class))
                        .map(
                                UserPreferenceEntityMapper.INSTANCE
                                        ::convertUserPreferenceEntityToUserModel)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Preferences not found for given user!"));

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(preferences);
    }

    @Override
    public ResponseEntity<Void> updatePreferences(UUID id, UserPreferenceDTO userPreferences) {
        UserEntity user =
                userService
                        .getUserById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                USER_NOT_FOUND_EXCEPTION_MESSAGE));

        if (!authorizationHandler.isAllowedForInstance("update", user)) {
            throw new AccessDeniedException("You do not have permission to modify this resource.");
        }

        userPreferenceService.updateUserPreferences(userPreferences, user);

        return ResponseEntity.ok().build();
    }

    private UserDTO mapUserEntity(UserEntity user) {
        UserDTO userDto = UserEntityMapper.INSTANCE.convertUserEntityToUserModel(user);
        userDto.setAssignedRoles(MapperUtils.mapUserEntityToAssignedRoleList(user));
        userDto.setPreferences(
                UserPreferenceEntityMapper.INSTANCE.convertUserPreferenceEntityToUserModel(
                        user.getUserPreference()));

        return userDto;
    }
}
