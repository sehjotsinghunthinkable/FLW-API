/*
 * AMRIT – Accessible Medical Records via Integrated Technology
 * Integrated EHR (Electronic Health Records) Solution
 *
 * Copyright (C) "Piramal Swasthya Management and Research Institute"
 *
 * This file is part of AMRIT.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */
package com.iemr.flw.seeder.migration;

import com.iemr.flw.domain.iemr.FormSection;
import com.iemr.flw.domain.iemr.FormVersion;
import com.iemr.flw.domain.iemr.QuestionOption;
import com.iemr.flw.domain.iemr.SectionQuestion;
import com.iemr.flw.dto.iemr.OptionConditionDTO;
import com.iemr.flw.dto.iemr.QuestionOptionDTO;
import com.iemr.flw.dto.iemr.QuestionValidationDTO;
import com.iemr.flw.dto.iemr.SectionQuestionDTO;
import com.iemr.flw.masterEnum.QuestionType;
import com.iemr.flw.masterEnum.ValidationType;
import com.iemr.flw.repo.iemr.QuestionOptionRepo;
import com.iemr.flw.repo.iemr.SectionQuestionRepo;
import com.iemr.flw.service.DynamicFormReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Restructures the Contact Follow Up form's "Screening & Referral" section (CFU_SEC_1) per the
 * revised Contact Follow-Up spec:
 *
 * 1. Soft-removes "Advised to visit health facility" (CFU_ADVISED_VISIT_FACILITY) and "Have you
 *    visited the health facility" (CFU_VISITED_FACILITY) — never hard-deleted, historical responses
 *    may reference them.
 * 2. Adds "Referred to PHI" (CFU_REFERRED_TO_PHI, mandatory Yes/No) as the first question of the
 *    section, in place of the removed CFU_ADVISED_VISIT_FACILITY. A new question rather than a
 *    relabel, so existing answers to the old question keep their original meaning.
 *    Adds "Have you visited the PHI" (CFU_VISITED_PHI, Yes/No) second, in place of the removed
 *    CFU_VISITED_FACILITY — shown only when "Referred to PHI" = Yes (SHOW_QUESTION condition, so it
 *    is hidden by default); "No" leaves it hidden and goes straight to submit.
 * 3. Renames "Status of Clinical Screening" (CFU_CLINICAL_SCREENING_STATUS) to "Status", changes it
 *    from RADIO to DROPDOWN, and shows it only when "Have you visited the PHI" = Yes.
 * 4. Renames its existing option rows in place (label and value), reordered per the spec. optionUuid
 *    is deliberately left unchanged so clients keep mapping existing answers to the same option:
 *    NO_TREATMENT → "TPT not recommended" (1), TPT_ELIGIBLE → "Recommend for TPT" (2),
 *    FULL_TREATMENT → "Recommend for ATT" (3). Both the question and its options are edited directly
 *    on the entities and saved via their repos (as V009 does), touching only the changed fields.
 * 5. Adds "Remarks" (CFU_REMARKS, optional free text, max 500 chars) as the last question, always shown.
 *
 * Routing a record on "Recommend for TPT" (TPT Follow up) / "Recommend for ATT" (Confirmed TB Cases)
 * is record-level logic outside the form definition and is not part of this migration.
 *
 * Every step is idempotent (lookups by uuid/optionValue, ensureX, same-value update; a renamed option
 * no longer matches its old value), so a re-run is a no-op.
 */
@Component
@RequiredArgsConstructor
public class V010_RestructureContactFollowUpStatus implements FormStructureMigration {

    private static final String FORM_UUID = "CONTACT_FOLLOW_UP";
    private static final String SECTION_UUID = "CFU_SEC_1";
    private static final String STATUS_QUESTION_UUID = "CFU_CLINICAL_SCREENING_STATUS";
    private static final String REFERRED_TO_PHI_UUID = "CFU_REFERRED_TO_PHI";
    private static final String VISITED_PHI_UUID = "CFU_VISITED_PHI";
    private static final String REMARKS_UUID = "CFU_REMARKS";
    private static final int REMARKS_MAX_LENGTH = 500;

    private static final List<String> REMOVED_QUESTION_UUIDS = List.of(
            "CFU_ADVISED_VISIT_FACILITY",
            "CFU_VISITED_FACILITY");

    private record OptionRename(String oldValue, String newValue, String label, String labelHindi, int order) {}

    private static final List<OptionRename> STATUS_OPTION_RENAMES = List.of(
            new OptionRename("NO_TREATMENT", "TPT_NOT_RECOMMENDED", "TPT not recommended", "टीपीटी की सिफारिश नहीं", 1),
            new OptionRename("TPT_ELIGIBLE", "RECOMMEND_FOR_TPT", "Recommend for TPT", "टीपीटी के लिए सिफारिश", 2),
            new OptionRename("FULL_TREATMENT", "RECOMMEND_FOR_ATT", "Recommend for ATT", "एटीटी के लिए सिफारिश", 3));

    private final QuestionOptionRepo optionRepo;
    private final SectionQuestionRepo questionRepo;

    @Override
    public String migrationId() {
        return "V010";
    }

    @Override
    public void apply(DynamicFormReconciliationService svc) {
        FormVersion version = svc.latestVersion(FORM_UUID)
                .orElseThrow(() -> new IllegalStateException(
                        FORM_UUID + " not found — must run after ContactFollowUpFormSeeder"));
        FormSection section = svc.section(version, SECTION_UUID)
                .orElseThrow(() -> new IllegalStateException("Section " + SECTION_UUID + " not found"));

        REMOVED_QUESTION_UUIDS.forEach(uuid -> svc.question(section, uuid)
                .ifPresent(q -> svc.unlinkQuestion(q.getQuestionId())));

        SectionQuestion referred = addYesNoQuestion(svc, section, REFERRED_TO_PHI_UUID,
                "Referred to PHI", "पीएचआई को रेफर किया गया", 1);
        addYesNoQuestion(svc, section, VISITED_PHI_UUID,
                "Have you visited the PHI", "क्या आपने पीएचआई का दौरा किया", 2);

        SectionQuestion status = svc.question(section, STATUS_QUESTION_UUID)
                .orElseThrow(() -> new IllegalStateException("Question " + STATUS_QUESTION_UUID + " not found"));

        status.setQuestionText("Status");
        status.setQuestionTextHindi("स्थिति");
        status.setQuestionType(QuestionType.DROPDOWN);
        questionRepo.save(status);

        STATUS_OPTION_RENAMES.forEach(rename -> svc.option(status, rename.oldValue()).ifPresent(opt -> {
            opt.setOptionLabel(rename.label());
            opt.setOptionLabelHindi(rename.labelHindi());
            opt.setOptionValue(rename.newValue());
            opt.setOptionValueHindi(rename.labelHindi());
            opt.setDisplayOrder(rename.order());
            optionRepo.save(opt);
        }));

        addRemarksQuestion(svc, section);

        // Fetched after all inserts: each ensureQuestion's displayOrder shift clears the persistence context.
        showWhenYes(svc, version, referred, VISITED_PHI_UUID);
        SectionQuestion visited = svc.question(section, VISITED_PHI_UUID)
                .orElseThrow(() -> new IllegalStateException("Question " + VISITED_PHI_UUID + " not found"));
        showWhenYes(svc, version, visited, STATUS_QUESTION_UUID);
    }

    private void showWhenYes(DynamicFormReconciliationService svc, FormVersion version, SectionQuestion source,
            String targetQuestionUuid) {
        QuestionOption yes = svc.option(source, "YES")
                .orElseThrow(() -> new IllegalStateException("Option " + source.getQuestionUuid() + "=YES not found"));
        OptionConditionDTO show = new OptionConditionDTO();
        show.setActionType("SHOW_QUESTION");
        show.setTargetQuestionUuid(targetQuestionUuid);
        svc.ensureCondition(yes, show, version);
    }

    // No displayOrder → appended after Status. The soft-removed questions still hold their slots, so an
    // explicit 4 would land Remarks above Status.
    private void addRemarksQuestion(DynamicFormReconciliationService svc, FormSection section) {
        SectionQuestionDTO dto = new SectionQuestionDTO();
        dto.setQuestionUuid(REMARKS_UUID);
        dto.setQuestionText("Remarks");
        dto.setQuestionTextHindi("टिप्पणियाँ");
        dto.setQuestionType(QuestionType.TEXT);
        dto.setIsMandatory(false);
        dto.setMaxLength(REMARKS_MAX_LENGTH);
        SectionQuestion remarks = svc.ensureQuestion(section, dto);

        QuestionValidationDTO maxLength = new QuestionValidationDTO();
        maxLength.setValidationType(ValidationType.MAX_LENGTH);
        maxLength.setValidationParam(String.valueOf(REMARKS_MAX_LENGTH));
        maxLength.setErrorMessage("Must be " + REMARKS_MAX_LENGTH + " characters or fewer");
        svc.ensureValidation(remarks, maxLength);
    }

    // ensureQuestion creates the question row only — its options are added separately via ensureOption.
    private SectionQuestion addYesNoQuestion(DynamicFormReconciliationService svc, FormSection section,
            String uuid, String text, String textHindi, int order) {
        SectionQuestionDTO dto = new SectionQuestionDTO();
        dto.setQuestionUuid(uuid);
        dto.setQuestionText(text);
        dto.setQuestionTextHindi(textHindi);
        dto.setQuestionType(QuestionType.RADIO);
        dto.setIsMandatory(true);
        dto.setDisplayOrder(order);
        SectionQuestion question = svc.ensureQuestion(section, dto);

        addOption(svc, question, "Yes", "हाँ", "YES", 1);
        addOption(svc, question, "No", "नहीं", "NO", 2);
        return question;
    }

    private void addOption(DynamicFormReconciliationService svc, SectionQuestion question, String label,
            String labelHindi, String value, int order) {
        QuestionOptionDTO dto = new QuestionOptionDTO();
        dto.setOptionLabel(label);
        dto.setOptionLabelHindi(labelHindi);
        dto.setOptionValue(value);
        dto.setOptionValueHindi(labelHindi);
        dto.setDisplayOrder(order);
        svc.ensureOption(question, dto);
    }
}
