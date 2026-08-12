package br.edu.unipam.tcc.service.impl;

import br.edu.unipam.tcc.dto.UnipamStudentProfileDto;
import br.edu.unipam.tcc.entity.UnipamAcademicRecord;
import br.edu.unipam.tcc.exception.ResourceNotFoundException;
import br.edu.unipam.tcc.repository.UnipamAcademicRecordRepository;
import br.edu.unipam.tcc.service.UnipamAcademicIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnipamAcademicIntegrationServiceImpl implements UnipamAcademicIntegrationService {

    private final UnipamAcademicRecordRepository academicRecordRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UnipamStudentProfileDto> findStudentByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Optional.empty();
        }

        String digitsOnly = sanitizePhone(phoneNumber);
        log.info("Consultando integração acadêmica UNIPAM por telefone: '{}' (dígitos: '{}')", phoneNumber, digitsOnly);

        // 1. Busca direta exata
        Optional<UnipamAcademicRecord> exact = academicRecordRepository.findByPhoneNumber(phoneNumber);
        if (exact.isPresent()) {
            return exact.map(this::toDto);
        }

        // 2. Busca por dígitos exatos
        Optional<UnipamAcademicRecord> byDigits = academicRecordRepository.findByPhoneNumber(digitsOnly);
        if (byDigits.isPresent()) {
            return byDigits.map(this::toDto);
        }

        // 3. Busca por sufixo de 8 ou 9 dígitos (DDD + número ou número sem DDI)
        List<UnipamAcademicRecord> allRecords = academicRecordRepository.findAll();
        for (UnipamAcademicRecord record : allRecords) {
            String recordDigits = sanitizePhone(record.getPhoneNumber());
            if (isPhoneMatch(digitsOnly, recordDigits)) {
                log.info("Match por sufixo na integração UNIPAM: '{}' com registro RA [{}]", phoneNumber, record.getRa());
                return Optional.of(toDto(record));
            }
        }

        log.warn("Nenhum registro acadêmico UNIPAM encontrado para o telefone: '{}'", phoneNumber);
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UnipamStudentProfileDto> findStudentByRa(String ra) {
        if (ra == null || ra.isBlank()) {
            return Optional.empty();
        }

        String cleanRa = ra.trim();
        log.info("Consultando integração acadêmica UNIPAM por RA: '{}'", cleanRa);
        return academicRecordRepository.findByRa(cleanRa).map(this::toDto);
    }

    @Override
    @Transactional
    public UnipamStudentProfileDto linkPhoneNumberToRa(String ra, String newPhoneNumber) {
        if (ra == null || ra.isBlank()) {
            throw new ResourceNotFoundException("RA não informado");
        }

        String cleanRa = ra.trim();
        UnipamAcademicRecord record = academicRecordRepository.findByRa(cleanRa)
                .orElseThrow(() -> new ResourceNotFoundException("Registro Acadêmico UNIPAM não encontrado para o RA: " + cleanRa));

        String sanitizedPhone = sanitizePhone(newPhoneNumber);
        log.info("Atualizando vínculo de telefone do RA [{}] para '{}'", cleanRa, sanitizedPhone);
        record.setPhoneNumber(sanitizedPhone);
        academicRecordRepository.save(record);

        return toDto(record);
    }

    private String sanitizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("\\D", "");
    }

    private boolean isPhoneMatch(String phone1, String phone2) {
        if (phone1.isEmpty() || phone2.isEmpty()) return false;
        if (phone1.equals(phone2)) return true;
        if (phone1.length() >= 8 && phone2.length() >= 8) {
            String suffix1 = phone1.length() > 8 ? phone1.substring(phone1.length() - 8) : phone1;
            String suffix2 = phone2.length() > 8 ? phone2.substring(phone2.length() - 8) : phone2;
            return suffix1.equals(suffix2);
        }
        return false;
    }

    private UnipamStudentProfileDto toDto(UnipamAcademicRecord record) {
        return UnipamStudentProfileDto.builder()
                .ra(record.getRa())
                .fullName(record.getFullName())
                .phoneNumber(record.getPhoneNumber())
                .courseCode(record.getCourse() != null ? record.getCourse().getCode() : null)
                .courseName(record.getCourse() != null ? record.getCourse().getName() : null)
                .academicPeriod(record.getAcademicPeriod())
                .email(record.getEmail())
                .active(record.getActive())
                .build();
    }
}
