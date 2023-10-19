package io.nuvalence.user.management.api.service.controller;

import io.nuvalence.auth.access.AuthorizationHandler;
import io.nuvalence.user.management.api.service.entity.PermissionEntity;
import io.nuvalence.user.management.api.service.entity.RoleEntity;
import io.nuvalence.user.management.api.service.generated.controllers.ApplicationApiDelegate;
import io.nuvalence.user.management.api.service.generated.models.ApplicationRolesDTO;
import io.nuvalence.user.management.api.service.generated.models.PermissionDTO;
import io.nuvalence.user.management.api.service.mapper.PermissionEntityMapper;
import io.nuvalence.user.management.api.service.service.ApplicationService;
import io.nuvalence.user.management.api.service.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles application-specific data like roles.
 */
@Service
@RequiredArgsConstructor
public class ApplicationApiDelegateImpl implements ApplicationApiDelegate {

    private final PermissionService permissionService;
    private final ApplicationService applicationService;
    private final AuthorizationHandler authorizationHandler;

    @Override
    public ResponseEntity<List<PermissionDTO>> getActiveApplicationRoles() {
        if (!authorizationHandler.isAllowed("view", RoleEntity.class)) {
            throw new AccessDeniedException("You do not have permission to access this resource.");
        }

        List<PermissionEntity> permissions = permissionService.getAllActivePermissions();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        permissions.stream()
                                .map(
                                        PermissionEntityMapper.INSTANCE
                                                ::permissionEntityToPermissionDto)
                                .collect(Collectors.toList()));
    }

    @Override
    public ResponseEntity<Void> updateApplicationRoles(ApplicationRolesDTO applicationRoles) {
        if (!authorizationHandler.isAllowed("register", RoleEntity.class)) {
            throw new AccessDeniedException("You do not have permission to access this resource.");
        }

        applicationService.setApplicationRoles(
                applicationRoles.getName(), applicationRoles.getRoles());

        return ResponseEntity.status(204).build();
    }
}
