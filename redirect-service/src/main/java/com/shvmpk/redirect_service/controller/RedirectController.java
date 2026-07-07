package com.shvmpk.redirect_service.controller;

import com.shvmpk.redirect_service.model.ShortCode;
import com.shvmpk.redirect_service.service.RedirectService;
import com.shvmpk.redirect_service.service.SecureCookieService;
import com.shvmpk.redirect_service.util.PasswordVerifier;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.Instant;

@Controller
@RequiredArgsConstructor
@Slf4j
@Hidden
public class RedirectController {

    private final RedirectService redirectService;
    private final PasswordVerifier passwordVerifier;
    private final SecureCookieService secureCookieService;

    @GetMapping("/{shortCodeOrAlias}")
    public String handleRedirect(
            @PathVariable String shortCodeOrAlias,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        ShortCode mapping = redirectService.resolveShortCodeOrAlias(shortCodeOrAlias);
        String canonicalShortCode = mapping.getShortCode();

        if (Boolean.TRUE.equals(mapping.getIsProtected())) {
            boolean isVerified = checkVerification(canonicalShortCode, request);
            if (isVerified) {
                String verifiedSource = request.getSession().getAttribute("verified_" + canonicalShortCode) != null
                        ? "SESSION" : "COOKIE";
                String targetUrl = redirectService.processRedirect(mapping, request, response);
                model.addAttribute("verifiedSource", verifiedSource);
                model.addAttribute("targetUrl", targetUrl);
                return "already-verified";
            }
            model.addAttribute("alias", canonicalShortCode);
            model.addAttribute("shortCodeOrAlias", shortCodeOrAlias);
            return "verify";
        }

        String targetUrl = redirectService.processRedirect(mapping, request, response);
        return "redirect:" + targetUrl;
    }

    @GetMapping("/{shortCodeOrAlias}/verify")
    public String showVerifyForm(@PathVariable String shortCodeOrAlias, Model model) {
        model.addAttribute("shortCodeOrAlias", shortCodeOrAlias);
        return "verify";
    }

    @PostMapping("/{shortCodeOrAlias}/verify")
    public String verifyPassword(
            @PathVariable String shortCodeOrAlias,
            @RequestParam String password,
            @RequestParam(required = false) String remember,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            Model model
    ) {
        ShortCode mapping = redirectService.resolveShortCodeOrAlias(shortCodeOrAlias);
        String canonicalShortCode = mapping.getShortCode();

        Integer attempts = (Integer) session.getAttribute("attempts:" + canonicalShortCode);
        Instant last = (Instant) session.getAttribute("lastAttempt:" + canonicalShortCode);
        if (attempts == null) attempts = 0;
        if (last != null && Duration.between(last, Instant.now()).toHours() < 6 && attempts >= 3) {
            model.addAttribute("error", "Too many failed attempts. Try again after 6 hours.");
            model.addAttribute("shortCodeOrAlias", shortCodeOrAlias);
            return "verify";
        }

        if (!passwordVerifier.verify(password, mapping.getPassword())) {
            attempts++;
            session.setAttribute("attempts:" + canonicalShortCode, attempts);
            session.setAttribute("lastAttempt:" + canonicalShortCode, Instant.now());
            model.addAttribute("error", "Incorrect password. Attempt " + attempts + " of 3.");
            model.addAttribute("shortCodeOrAlias", shortCodeOrAlias);
            return "verify";
        }

        session.setAttribute("verified_" + canonicalShortCode, true);
        session.removeAttribute("attempts:" + canonicalShortCode);
        session.removeAttribute("lastAttempt:" + canonicalShortCode);

        if (remember != null) {
            Cookie cookie = secureCookieService.createCookie(
                    "verified_" + canonicalShortCode, "true",
                    "/" + shortCodeOrAlias, 60 * 60 * 24 * 7);
            response.addCookie(cookie);
        }

        String targetUrl = redirectService.processRedirect(mapping, request, response);
        return "redirect:" + targetUrl;
    }

    private boolean checkVerification(String canonicalShortCode, HttpServletRequest request) {
        HttpSession session = request.getSession();
        if (Boolean.TRUE.equals(session.getAttribute("verified_" + canonicalShortCode))) {
            return true;
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (("verified_" + canonicalShortCode).equals(cookie.getName()) &&
                        secureCookieService.matchesDecryption(cookie.getValue(), "true")) {
                    return true;
                }
            }
        }
        return false;
    }
}
