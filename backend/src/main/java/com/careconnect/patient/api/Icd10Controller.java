package com.careconnect.patient.api;

import java.util.List;

import com.careconnect.patient.service.Icd10Service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/icd10")
public class Icd10Controller {

    private final Icd10Service service;

    public Icd10Controller(Icd10Service service) {
        this.service = service;
    }

    @GetMapping
    @ChartWriteStaff
    public List<Icd10Response> search(@RequestParam(defaultValue = "") String q) {
        return service.search(q).stream().map(Icd10Response::from).toList();
    }
}
