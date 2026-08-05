#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[2])
errors: list[str] = []


def read(path: str) -> str:
    return (root / path).read_text(encoding='utf-8')


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)

recommendation = read('backend-java/server/src/main/java/com/wust/dormitory/student/StudentRoomRecommendationService.java')
require('candidates.getFirst()' not in recommendation,
        'recommendation endpoint still returns the first score-sorted room')
require('ORDER BY target_bed.position_index\n                LIMIT 1' not in recommendation,
        'bed recommendation still returns the first database row')
require('RecommendationSampler.bestMatch' in recommendation,
        'BEST_MATCH is not wired to the stable best-match sampler')
require('RecommendationSampler.trueRandom' in recommendation,
        'TRUE_RANDOM is not wired to uniform business sampling')
require('RecommendationSampler.weightedRandom' in recommendation,
        'MATCH_WEIGHTED_RANDOM is not wired to weighted sampling')

feature_access = read('backend-java/server/src/main/java/com/wust/dormitory/subscription/FeatureAccessService.java')
require('FeatureAccessEvaluator.evaluate' in feature_access,
        'effective feature access does not use the double-layer evaluator')
require('school_feature_setting' in feature_access and 'school_controllable' in feature_access,
        'feature access does not read school-controllable metadata and school settings')

home = read('frontend/src/views/student/StudentHomeContent.vue')
require('// @ts-nocheck' not in home,
        'student home still disables TypeScript checking')
if (root / 'frontend/src/views/student/StudentHomeContent.logic.ts').exists():
    logic = read('frontend/src/views/student/StudentHomeContent.logic.ts')
    template = read('frontend/src/views/student/StudentHomeContent.template.html')
    require('i18nSubtitle' in home and 'i18nSubtitle' in logic and 'i18nSubtitle(' in template,
            'split student home does not expose a stable subtitle function to its external template')

if errors:
    print('\n'.join(f'- {error}' for error in errors))
    raise SystemExit(1)
print('Entitlement, recommendation, concurrency contracts passed')
