package br.edu.unipam.tcc.service;

import br.edu.unipam.tcc.dto.UnipamStudentProfileDto;

import java.util.Optional;

public interface UnipamAcademicIntegrationService {

    /**
     * Busca o registro acadêmico do estudante na base UNIPAM pelo número de telefone sanitizado.
     *
     * @param phoneNumber Número de telefone com ou sem DDI/DDD
     * @return Perfil acadêmico institucional caso encontrado
     */
    Optional<UnipamStudentProfileDto> findStudentByPhoneNumber(String phoneNumber);

    /**
     * Busca o registro acadêmico do estudante na base UNIPAM pelo RA institucional (ex: 23000388).
     *
     * @param ra Registro Acadêmico oficial do aluno
     * @return Perfil acadêmico institucional caso encontrado
     */
    Optional<UnipamStudentProfileDto> findStudentByRa(String ra);

    /**
     * Vincula ou atualiza o número de telefone de contato do estudante associado a um determinado RA.
     *
     * @param ra Registro Acadêmico
     * @param newPhoneNumber Novo número de telefone
     * @return Perfil acadêmico atualizado
     */
    UnipamStudentProfileDto linkPhoneNumberToRa(String ra, String newPhoneNumber);
}
