package com.centrral.centralres.core.security.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.centrral.centralres.core.aws.model.FileMetadata;
import com.centrral.centralres.core.aws.service.FileService;
import com.centrral.centralres.core.exceptions.EmailAlreadyExistsException;
import com.centrral.centralres.core.exceptions.EmailChangeNotAllowedException;
import com.centrral.centralres.core.exceptions.InvalidPasswordException;
import com.centrral.centralres.core.exceptions.UserNotFoundException;
import com.centrral.centralres.core.exceptions.UsernameAlreadyExistsException;
import com.centrral.centralres.core.exceptions.UsernameChangeNotAllowedException;
import com.centrral.centralres.core.security.dto.ChanguePasswordRequest;
import com.centrral.centralres.core.security.dto.CreateStaffUserRequest;
import com.centrral.centralres.core.security.dto.UpdateProfileRequest;
import com.centrral.centralres.core.security.dto.UpdateProfileResponse;
import com.centrral.centralres.core.security.dto.UpdateUserRequest;
import com.centrral.centralres.core.security.dto.UserProfileResponse;
import com.centrral.centralres.core.security.dto.UserSessionResponse;
import com.centrral.centralres.core.security.enums.AuthProvider;
import com.centrral.centralres.core.security.jwt.JwtService;
import com.centrral.centralres.core.security.model.RefreshToken;
import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.core.security.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FileService fileService;
    // private final NotificationProducer notificationProducer;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<UserSessionResponse> getSessions(User user, String currentAccessToken) {
        Optional<RefreshToken> currentSession = authService.getRefreshTokenByAccessToken(currentAccessToken);

        String currentSessionId = currentSession.map(rt -> rt.getId().toString()).orElse("");

        return authService.getUserSessions(user, currentSessionId);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> getUsersForAdmin(User requestingUser) {
        boolean isAdmin = requestingUser.getRoles().stream()
                .map(Role::getName)
                .anyMatch(r -> r.equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new AccessDeniedException("Acceso denegado: solo administradores");
        }

        Set<String> excludedRoles = Set.of("ROLE_CLIENT", "ROLE_SUPPLIER");

        List<User> users = userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .map(Role::getName)
                        .noneMatch(excludedRoles::contains))
                .collect(Collectors.toList());

        return users.stream()
                .map(u -> getUserByUsername(u.getUsername()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(User requestingUser, Long userId) {
        boolean isAdmin = requestingUser.getRoles().stream()
                .map(Role::getName)
                .anyMatch(r -> r.equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new AccessDeniedException("Acceso denegado: solo administradores");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .provider(user.getProvider().name())
                .hasPassword(user.getPassword() != null && !user.getPassword().isEmpty())
                .profileImageUrl(
                        user.getProfileImageId() != null ? fileService.getFileUrl(user.getProfileImageId()) : null)
                .build();
    }

    /**
     * Crea una cuenta de personal sin usar los datos semilla del perfil de desarrollo.
     * Solo un administrador autenticado puede invocar esta operación.
     */
    @Transactional
    public UserProfileResponse createStaffUser(User requestingUser, CreateStaffUserRequest request) {
        if (requestingUser == null || !requestingUser.hasRole("ROLE_ADMIN")) {
            throw new AccessDeniedException("Solo un administrador puede crear usuarios del equipo.");
        }

        Set<String> allowedRoles = Set.of(
                "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_WAITER", "ROLE_CHEF", "ROLE_CASHIER");
        if (!allowedRoles.contains(request.getRole())) {
            throw new IllegalArgumentException("Rol de personal no permitido.");
        }

        String username = request.getUsername().trim().toLowerCase(java.util.Locale.ROOT);
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException("Nombre de usuario ya registrado.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException("Correo electrónico ya registrado.");
        }

        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new IllegalArgumentException("El rol no está configurado en la base de datos."));

        User staff = User.builder()
                .username(username)
                .email(email)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(role))
                .enabled(true)
                .emailVerified(true)
                .provider(AuthProvider.LOCAL)
                .build();

        User saved = userRepository.save(staff);
        return getUserByUsername(saved.getUsername());
    }

    @Transactional
    public User createNewUser(String username, String email, String password,
            String firstName, String lastName, String phone) {

        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException("Username ya existe");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException("Email ya existe");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .roles(new HashSet<>())
                .enabled(true)
                .emailVerified(true)
                .provider(AuthProvider.LOCAL)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public UpdateProfileResponse updateProfile(User user, UpdateProfileRequest request) {
        boolean loginChanged = false;
        boolean passwordRequired = false;

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());

        String newEmail = request.getEmail().toLowerCase();

        if (!newEmail.equals(user.getEmail().toLowerCase())) {

            if (user.getProvider() != AuthProvider.LOCAL
                    && (user.getPassword() == null || user.getPassword().isEmpty())) {
                passwordRequired = true;
                throw new InvalidPasswordException(
                        "Debes establecer una contraseña antes de cambiar tu correo.");
            }

            if (user.getLastEmailChange() != null) {
                LocalDateTime nextAllowedEmailChange = user.getLastEmailChange().plusDays(30);
                if (LocalDateTime.now().isBefore(nextAllowedEmailChange)) {
                    long daysLeft = java.time.Duration.between(LocalDateTime.now(), nextAllowedEmailChange).toDays();
                    throw new EmailChangeNotAllowedException(
                            "No puedes cambiar tu email aún. Te faltan " + daysLeft + " días.");
                }
            }

            if (userRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new EmailChangeNotAllowedException("El email ya está en uso");
            }

            user.setEmail(newEmail);
            user.setLastEmailChange(LocalDateTime.now());
            user.setEmailVerified(false);

            if (user.getProvider() != AuthProvider.LOCAL) {
                user.setGoogleId(null);
                user.setFacebookId(null);
                user.setGithubId(null);
                user.setProvider(AuthProvider.LOCAL);
                passwordRequired = true;
            }

            String verificationCode = UUID.randomUUID().toString();
            user.setVerificationCode(verificationCode);

            userRepository.save(user);

            // EmailVerificationEvent event = EmailVerificationEvent.builder()
            // .userId(user.getId())
            // .recipient(newEmail)
            // .subject("Verifica tu nuevo correo")
            // .message("Por favor verifica tu correo haciendo clic en el enlace:")
            // .actionUrl(frontendUrl + "/verify-email?code=" + verificationCode)
            // .build();
            // notificationProducer.send("notifications", event);

            loginChanged = true;
        }

        if (!request.getUsername().equals(user.getUsername())) {

            if (user.getLastUsernameChange() != null) {
                LocalDateTime nextAllowedUsernameChange = user.getLastUsernameChange().plusDays(7);
                if (LocalDateTime.now().isBefore(nextAllowedUsernameChange)) {
                    long daysLeft = java.time.Duration.between(LocalDateTime.now(), nextAllowedUsernameChange).toDays();
                    throw new UsernameChangeNotAllowedException(
                            "No puedes cambiar tu username aún. Te faltan " + daysLeft + " días.");
                }
            }

            if (userRepository.existsByUsername(request.getUsername())) {
                throw new UsernameChangeNotAllowedException("El username ya está en uso");
            }

            user.setUsername(request.getUsername());
            user.setLastUsernameChange(LocalDateTime.now());
            loginChanged = true;
        }

        userRepository.save(user);

        UserProfileResponse userProfile = getUserByUsername(user.getUsername());

        String newToken = null;
        if (loginChanged && !passwordRequired) {
            newToken = jwtService.generateAccessToken(
                    user.getUsername(),
                    Map.of("roles", user.getRoles().stream().map(Role::getName).toList()));
        }

        return UpdateProfileResponse.builder()
                .user(userProfile)
                .token(newToken)
                .build();
    }

    @Transactional
    public UserProfileResponse updateProfileImage(User user, MultipartFile file) {
        FileMetadata metadata = fileService.uploadFile(file, "profiles");

        if (user.getProfileImageId() != null) {
            fileService.deleteFile(user.getProfileImageId());
        }

        user.setProfileImageId(metadata.getId());
        userRepository.save(user);

        UserProfileResponse profile = getUserByUsername(user.getUsername());
        profile.setProfileImageUrl(metadata.getUrl());
        return profile;
    }

    @Transactional
    public UserProfileResponse updateUserById(User requestingUser, Long userId, UpdateUserRequest request) {
        boolean isAdmin = requestingUser.getRoles().stream()
                .map(Role::getName)
                .anyMatch(r -> r.equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new AccessDeniedException("Acceso denegado: solo administradores pueden modificar usuarios.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));

        user.setUsername(request.getUsername());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setEnabled(request.isEnabled());

        if (requestingUser.getId().equals(user.getId()) && !request.isEnabled()) {
            throw new IllegalArgumentException("No puedes desactivar tu propia cuenta administradora.");
        }

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            if (requestingUser.getId().equals(user.getId())
                    && !request.getRoles().contains("ROLE_ADMIN")) {
                throw new IllegalArgumentException("No puedes quitarte el rol ADMIN a ti mismo.");
            }
            Set<Role> newRoles = request.getRoles().stream()
                    .map(roleName -> roleRepository.findByName(roleName)
                            .orElseThrow(() -> new IllegalArgumentException("Rol desconocido: " + roleName)))
                    .collect(Collectors.toSet());
            user.setRoles(newRoles);
        }

        userRepository.save(user);

        return getUserByUsername(user.getUsername());
    }

    @Transactional
    public void changePassword(String username, ChanguePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            if (request.getCurrentPassword() == null ||
                    !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new InvalidPasswordException("La contraseña actual es incorrecta");
            }
        } else {
            if (request.getCurrentPassword() != null && !request.getCurrentPassword().isEmpty()) {
                throw new InvalidPasswordException(
                        "No necesitas contraseña actual para usuarios OAuth sin password");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        authService.revokeAllRefreshTokens(user);
    }

    public UserProfileResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));

        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        String profileImageUrl = null;
        if (user.getProfileImageId() != null) {
            try {
                profileImageUrl = fileService.getFileUrl(user.getProfileImageId());
            } catch (Exception e) {
                profileImageUrl = null;
            }
        }

        LocalDateTime usernameNextChange = null;
        if (user.getLastUsernameChange() != null) {
            usernameNextChange = user.getLastUsernameChange().plusDays(7);
        }

        LocalDateTime emailNextChange = null;
        if (user.getLastEmailChange() != null) {
            emailNextChange = user.getLastEmailChange().plusDays(30);
        }

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .roles(roles)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .provider(user.getProvider().name())
                .hasPassword(user.getPassword() != null && !user.getPassword().isEmpty())
                .profileImageUrl(profileImageUrl)
                .usernameNextChange(usernameNextChange)
                .emailNextChange(emailNextChange)
                .build();
    }

}
