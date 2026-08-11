package br.edu.unipam.tcc.controller;

import br.edu.unipam.tcc.dto.ApiErrorResponse;
import br.edu.unipam.tcc.exception.BusinessException;
import br.edu.unipam.tcc.exception.ExcelImportException;
import br.edu.unipam.tcc.exception.ResourceNotFoundException;
import br.edu.unipam.tcc.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageService messageService;

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler(messageService);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
    }

    @Test
    @DisplayName("Deve tratar ResourceNotFoundException e retornar HTTP 404 com DTO padronizado")
    void shouldHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("error.resource_not_found", "ID_123");
        when(messageService.getMessage(eq("error.resource_not_found"), any()))
                .thenReturn("Recurso não encontrado: ID_123");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Recurso não encontrado: ID_123");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    @Test
    @DisplayName("Deve tratar BusinessException e retornar HTTP 422 com DTO padronizado")
    void shouldHandleBusinessException() {
        BusinessException ex = new BusinessException("error.business_rule", "Aluno inativo");
        when(messageService.getMessage(eq("error.business_rule"), any()))
                .thenReturn("Violação de regra: Aluno inativo");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(422);
        assertThat(response.getBody().getMessage()).isEqualTo("Violação de regra: Aluno inativo");
    }

    @Test
    @DisplayName("Deve tratar ExcelImportException e retornar HTTP 400 com DTO padronizado")
    void shouldHandleExcelImportException() {
        ExcelImportException ex = new ExcelImportException("error.excel_import", "Coluna A ausente");
        when(messageService.getMessage(eq("error.excel_import"), any()))
                .thenReturn("Falha na planilha: Coluna A ausente");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleExcelImportException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Falha na planilha: Coluna A ausente");
    }

    @Test
    @DisplayName("Deve tratar Exception genérica e retornar HTTP 500 com DTO padronizado")
    void shouldHandleGenericException() {
        Exception ex = new NullPointerException("NPE");
        when(messageService.getMessage("error.generic"))
                .thenReturn("Erro interno inesperado no servidor.");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("Erro interno inesperado no servidor.");
    }
}
