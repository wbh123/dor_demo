#!/usr/bin/env python3
from __future__ import annotations

import validate_system_contracts as legacy

STALE_WELCOME_ERROR = "country-specific welcome messages or English fallback are missing"
_original_comprehensive_validation = legacy.validate_comprehensive_enhancements


def validate_comprehensive_enhancements(errors: list[str]) -> None:
    legacy_errors: list[str] = []
    _original_comprehensive_validation(legacy_errors)
    errors.extend(error for error in legacy_errors if error != STALE_WELCOME_ERROR)

    dashboard = legacy.read("frontend/src/views/admin/AdminDashboardView.vue")
    welcome_service = legacy.read(
        "backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java"
    )
    setting_service = legacy.read(
        "backend-java/server/src/main/java/com/wust/dormitory/admin/SystemSettingService.java"
    )
    legacy.require(
        "languageMessages" in dashboard
        and "addWelcomeLanguage" in dashboard
        and "removeWelcomeLanguage" in dashboard
        and "countryMessages" not in dashboard,
        "administrator welcome editor is not locale-based or still exposes country-specific copies",
        errors,
    )
    legacy.require(
        "renderedMessages" in welcome_service
        and 'configuration.messages().get("en-US")' in welcome_service
        and "countryMessages" not in welcome_service,
        "student welcome service does not return locale versions with administrator English fallback",
        errors,
    )
    legacy.require(
        'FALLBACK_WELCOME_LOCALE = "en-US"' in setting_service
        and "normalizeLocaleMessages" in setting_service,
        "welcome configuration does not enforce administrator-managed locale versions",
        errors,
    )


legacy.validate_comprehensive_enhancements = validate_comprehensive_enhancements

if __name__ == "__main__":
    raise SystemExit(legacy.main())
