package com.careconnect.patient.service;

import java.util.ArrayList;
import java.util.List;

import com.careconnect.patient.domain.Patient;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

/** Turns a parsed search into a database condition. User text is always a bound value, never SQL. */
final class PatientSearchSpecifications {

    private static final char ESCAPE = '\\';

    private PatientSearchSpecifications() {
    }

    static Specification<Patient> of(PatientSearchQuery query) {
        return switch (query) {
            case PatientSearchQuery.ByDateOfBirth q ->
                (root, cq, cb) -> cb.equal(root.get("dateOfBirth"), q.dateOfBirth());
            case PatientSearchQuery.ByMrn q ->
                (root, cq, cb) -> cb.equal(root.get("mrn"), q.mrn());
            case PatientSearchQuery.ByPhone q ->
                (root, cq, cb) -> cb.like(cb.function("regexp_replace", String.class, root.get("phone"),
                    cb.literal("[^0-9]"), cb.literal(""), cb.literal("g")), "%" + q.digits());
            case PatientSearchQuery.ByName q -> (root, cq, cb) -> {
                List<Predicate> words = new ArrayList<>();
                for (String word : q.words()) {
                    String pattern = escape(word) + "%";
                    words.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern, ESCAPE),
                        cb.like(cb.lower(root.get("lastName")), pattern, ESCAPE)));
                }
                return cb.and(words.toArray(Predicate[]::new));
            };
        };
    }

    /** A typed % or _ must find nothing special: otherwise "%" would list every patient. */
    private static String escape(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
