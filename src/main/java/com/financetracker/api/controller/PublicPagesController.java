package com.financetracker.api.controller;

import com.financetracker.api.dto.settings.SettingsDtos.DeleteAccountRequest;
import com.financetracker.api.entity.User;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.UserRepository;
import com.financetracker.api.service.SettingsService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The public web pages Google Play requires a URL for: the privacy policy and a way to delete an
 * account without installing the app. Plain HTML forms, no JavaScript.
 */
@Hidden
@Controller
public class PublicPagesController {

    private final UserRepository userRepo;
    private final SettingsService settingsService;

    public PublicPagesController(UserRepository userRepo, SettingsService settingsService) {
        this.userRepo = userRepo;
        this.settingsService = settingsService;
    }

    @GetMapping("/delete-account")
    public String deleteAccountPage() {
        return "forward:/delete-account.html";
    }

    @GetMapping("/privacy")
    public String privacyPage() {
        return "forward:/privacy.html";
    }

    /** Same check and same effect as the in-app delete; rate-limited like sign-in (RateLimitFilter). */
    @PostMapping(value = "/delete-account", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String deleteAccount(@RequestParam String email, @RequestParam String password) {
        boolean deleted = false;
        try {
            User user = userRepo.findByEmail(email.strip()).filter(u -> u.getDeletedAt() == null).orElse(null);
            if (user != null) {
                settingsService.deleteAccount(user.getId(), new DeleteAccountRequest(password, "DELETE"));
                deleted = true;
            }
        } catch (ApiException wrongPassword) {
            // fall through: one answer for "no such account" and "wrong password"
        }
        return page(deleted ? "Your account is deleted"
                        : "That email and password don't match",
                deleted ? "You're signed out everywhere. Your data will be erased within 30 days."
                        : "Nothing was deleted. <a href=\"/delete-account\">Try again</a>.");
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>" + title + "</title></head>"
                + "<body style=\"font:16px/1.5 system-ui,sans-serif;max-width:440px;margin:48px auto;padding:0 16px\">"
                + "<h1 style=\"font-size:22px\">" + title + "</h1><p>" + body + "</p></body></html>";
    }
}
