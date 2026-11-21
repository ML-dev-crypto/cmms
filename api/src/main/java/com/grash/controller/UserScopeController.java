package com.grash.controller;

import com.grash.model.User;
import com.grash.model.UserScope;
import com.grash.security.CurrentUser;
import com.grash.service.UserScopeService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user-scopes")
@Tag(name = "User scopes", description = "Hospital, department and location scopes per user")
@RequiredArgsConstructor
public class UserScopeController {

    private final UserScopeService userScopeService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<UserScope> getMine(@Parameter(hidden = true) @CurrentUser User user) {
        return userScopeService.findMine(user);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<UserScope> getByUser(@PathVariable Long userId,
                                     @Parameter(hidden = true) @CurrentUser User user) {
        return userScopeService.findByUser(userId, user);
    }

    @PutMapping("/user/{userId}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<UserScope> replace(@PathVariable Long userId,
                                   @RequestBody List<UserScope> scopes,
                                   @Parameter(hidden = true) @CurrentUser User user) {
        return userScopeService.replaceForUser(userId, scopes, user);
    }
}
