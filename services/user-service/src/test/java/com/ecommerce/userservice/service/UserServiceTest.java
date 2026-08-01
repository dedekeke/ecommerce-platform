package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserRole;
import com.ecommerce.userservice.exception.EmailAlreadyRegisteredException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private String auth0Id;
    private Map<String, Object> claims;
    private User existingUser;

    @BeforeEach
    void setUp() {
        auth0Id = "auth0|123456";
        claims = new HashMap<>();
        claims.put("email", "test@example.com");
        claims.put("given_name", "John");
        claims.put("family_name", "Doe");
        claims.put("email_verified", true);
        claims.put("picture", "https://example.com/photo.jpg");

        existingUser = User.builder()
                .id(1L)
                .auth0Id(auth0Id)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .role(UserRole.USER)
                .active(true)
                .build();
    }

    @Test
    void getOrCreateFromAuth0_existingUser_shouldUpdateLastLogin() {
        // Given
        when(userRepository.findByAuth0Id(auth0Id)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        User result = userService.getOrCreateFromAuth0(auth0Id, claims);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAuth0Id()).isEqualTo(auth0Id);
        assertThat(result.getLastLoginAt()).isNotNull();
        verify(userRepository).findByAuth0Id(auth0Id);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void getOrCreateFromAuth0_newUser_shouldCreateUser() {
        // Given
        when(userRepository.findByAuth0Id(auth0Id)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(1L);
            return user;
        });

        // When
        User result = userService.getOrCreateFromAuth0(auth0Id, claims);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAuth0Id()).isEqualTo(auth0Id);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getEmailVerified()).isTrue();
        assertThat(result.getRole()).isEqualTo(UserRole.USER);
        assertThat(result.getActive()).isTrue();
        assertThat(result.getLastLoginAt()).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void getOrCreateFromAuth0_emailExistsWithDifferentAuth0Id_shouldThrowConflict() {
        // Given: no user for the incoming Auth0 id, but the email is already
        // taken by a DIFFERENT account (e.g. a pre-created/spoofed row). This is
        // the DoS vector: the raw insert would hit the unique-email constraint
        // and surface as an opaque 500, blocking the victim's first login.
        String incomingAuth0Id = "auth0|victim-real";
        when(userRepository.findByAuth0Id(incomingAuth0Id)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));

        // When / Then: a controlled conflict is raised instead of a raw 500,
        // and we never silently link the new Auth0 id to the existing record.
        assertThatThrownBy(() -> userService.getOrCreateFromAuth0(incomingAuth0Id, claims))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("test@example.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getOrCreateFromAuth0_newUserNoEmailCollision_shouldCreateUser() {
        // Given: neither the Auth0 id nor the email exist yet.
        when(userRepository.findByAuth0Id(auth0Id)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(1L);
            return user;
        });

        // When
        User result = userService.getOrCreateFromAuth0(auth0Id, claims);

        // Then: happy-path first login still provisions the account.
        assertThat(result).isNotNull();
        assertThat(result.getAuth0Id()).isEqualTo(auth0Id);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void findByAuth0Id_userExists_shouldReturnUser() {
        // Given
        when(userRepository.findByAuth0Id(auth0Id)).thenReturn(Optional.of(existingUser));

        // When
        Optional<User> result = userService.findByAuth0Id(auth0Id);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getAuth0Id()).isEqualTo(auth0Id);
        verify(userRepository).findByAuth0Id(auth0Id);
    }

    @Test
    void updateProfile_userExists_shouldUpdateAndReturnUser() {
        // Given
        when(userRepository.findByAuth0Id(auth0Id)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        User result = userService.updateProfile(auth0Id, "Jane", "Smith", "+12125551234");

        // Then
        assertThat(result.getFirstName()).isEqualTo("Jane");
        assertThat(result.getLastName()).isEqualTo("Smith");
        assertThat(result.getPhoneNumber()).isEqualTo("+12125551234");
        verify(userRepository).findByAuth0Id(auth0Id);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateProfile_userNotFound_shouldThrowException() {
        // Given
        when(userRepository.findByAuth0Id(auth0Id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> userService.updateProfile(auth0Id, "Jane", "Smith", "+12125551234"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void deactivateUser_userExists_shouldSetActiveToFalse() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        userService.deactivateUser(1L);

        // Then
        assertThat(existingUser.getActive()).isFalse();
        verify(userRepository).save(existingUser);
    }

    @Test
    void changeUserRole_userExists_shouldUpdateRole() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        userService.changeUserRole(1L, UserRole.ADMIN);

        // Then
        assertThat(existingUser.getRole()).isEqualTo(UserRole.ADMIN);
        verify(userRepository).save(existingUser);
    }

}
