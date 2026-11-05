package com.livemcq.auth;

import com.livemcq.domain.AppUser;
import com.livemcq.domain.Institution;
import com.livemcq.domain.Role;
import com.livemcq.domain.Section;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.InstitutionRepository;
import com.livemcq.repo.SectionRepository;
import com.livemcq.security.AuthPrincipal;
import com.livemcq.security.IssuedToken;
import com.livemcq.security.JwtService;
import com.livemcq.web.ApiException;
import com.livemcq.web.Text;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;   // BCrypt only looks at the first 72 bytes

    private final InstitutionRepository institutions;
    private final AppUserRepository users;
    private final SectionRepository sections;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String dummyHash;

    public AuthService(InstitutionRepository institutions, AppUserRepository users, SectionRepository sections,
                       PasswordEncoder encoder, JwtService jwt) {
        this.institutions = institutions;
        this.users = users;
        this.sections = sections;
        this.encoder = encoder;
        this.jwt = jwt;
        // Compared against when the login does not exist, so "unknown user" costs as much time as
        // "wrong password" and response time cannot be used to discover which rolls exist.
        this.dummyHash = encoder.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // the DB constraint forces slugs to lower case, so "Demo " and "demo" are the same institution
        String slug = request.institution().trim().toLowerCase(Locale.ROOT);
        Optional<Institution> institution = institutions.findBySlug(slug);
        Optional<AppUser> user = institution.flatMap(i -> users.findByRoll(i.getId(), request.roll().trim()));

        String hash = user.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = request.password().getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
                && encoder.matches(request.password(), hash);

        // ONE message for every failure: never tell the caller which of the three inputs was wrong
        if (user.isEmpty() || !passwordOk) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Institution, roll or password is incorrect");
        }
        AppUser u = user.get();
        IssuedToken token = jwt.issue(u.getId(), u.getInstitutionId(), u.getRole());
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), toView(u, institution.get()));
    }

    /** Student self sign-up; on success the student is logged in straight away. */
    @Transactional
    public LoginResponse register(RegisterRequest r) {
        Institution institution = institutions.findBySlug(r.institution().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("INSTITUTION_NOT_FOUND", "Institution not found"));
        String name = Text.trim(r.name());
        String roll = Text.asciiDigits(Text.trim(r.roll()));
        if (name.length() < 3) {
            throw ApiException.unprocessable("NAME_TOO_SHORT", "Enter your full name (at least 3 characters)");
        }
        if (!roll.matches("\\d{2,8}")) {
            throw ApiException.unprocessable("ROLL_INVALID", "Roll must be digits only, 2 to 8 digits long");
        }
        if (r.password().length() < 4) {
            throw ApiException.unprocessable("PASSWORD_TOO_SHORT", "Password must be at least 4 characters");
        }
        if (r.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw ApiException.unprocessable("PASSWORD_TOO_LONG", "Password is too long");
        }
        Section section = sections.findByIdAndInstitutionId(r.sectionId(), institution.getId())
                .orElseThrow(() -> ApiException.unprocessable("SECTION_INVALID", "Choose a valid class and section"));
        if (users.findByRoll(institution.getId(), roll).isPresent()) {
            throw ApiException.conflict("ROLL_TAKEN", "An account with this roll already exists");
        }
        try {
            AppUser u = users.saveAndFlush(new AppUser(institution.getId(), Role.STUDENT, name, roll,
                    encoder.encode(r.password()), section));
            IssuedToken token = jwt.issue(u.getId(), u.getInstitutionId(), u.getRole());
            return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), toView(u, institution));
        } catch (DataIntegrityViolationException e) {   // same roll registered twice at the same instant
            throw ApiException.conflict("ROLL_TAKEN", "An account with this roll already exists");
        }
    }

    /** The current user, re-read from the database: a deleted account stops working even with an unexpired token. */
    @Transactional(readOnly = true)
    public UserView me(AuthPrincipal principal) {
        AppUser u = users.findById(principal.userId())
                .filter(found -> found.getInstitutionId().equals(principal.institutionId()))
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "This account no longer exists"));
        Institution institution = institutions.findById(u.getInstitutionId()).orElseThrow();
        return toView(u, institution);
    }

    private static UserView toView(AppUser u, Institution i) {
        Section s = u.getSection();   // lazy: loaded here, inside the transaction
        return new UserView(u.getId(), u.getName(), u.getRoll(), u.getRole(),
                new UserView.InstitutionView(i.getId(), i.getName(), i.getSlug()),
                s == null ? null : s.getSchoolClass().getId(),
                s == null ? null : s.getSchoolClass().getName(),
                s == null ? null : s.getId(),
                s == null ? null : s.getName());
    }
}
