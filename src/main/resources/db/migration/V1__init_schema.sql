-- =============================================================================
-- Migration: V1__init_schema.sql
-- Descrição: Criação das tabelas centrais do Chatbot MMEEBB para Medicina
-- =============================================================================

-- Habilita a extensão pgvector para busca semântica
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- 1. Tabela de Especialidades Médicas
CREATE TABLE IF NOT EXISTS specialties (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. Tabela de Tópicos / Temas Clínicos
CREATE TABLE IF NOT EXISTS topics (
    id BIGSERIAL PRIMARY KEY,
    specialty_id BIGINT NOT NULL REFERENCES specialties(id) ON DELETE RESTRICT,
    name VARCHAR(150) NOT NULL,
    summary_text TEXT,
    embedding vector(1536),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 3. Tabela de Estudantes de Medicina
CREATE TABLE IF NOT EXISTS students (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number VARCHAR(30) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    intern_period INT NOT NULL DEFAULT 9,
    preferred_study_time TIME DEFAULT '08:00:00' NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 4. Tabela de Questões / Flashcards Médicos
CREATE TABLE IF NOT EXISTS questions (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES topics(id) ON DELETE RESTRICT,
    question_type VARCHAR(30) NOT NULL DEFAULT 'MULTIPLE_CHOICE', -- MULTIPLE_CHOICE, FLASHCARD
    statement TEXT NOT NULL,
    clinical_explanation TEXT NOT NULL,
    difficulty VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',             -- EASY, MEDIUM, HARD
    source VARCHAR(100),                                          -- ex: UNIPAM 2025/1
    embedding vector(1536),
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 5. Tabela de Alternativas das Questões
CREATE TABLE IF NOT EXISTS question_options (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    letter CHAR(1) NOT NULL,                                     -- 'A', 'B', 'C', 'D', 'E'
    option_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_question_letter UNIQUE (question_id, letter)
);

-- 6. Tabela de Agendamento do Motor MMEEBB (Repetição Espaçada Binária 2^n)
CREATE TABLE IF NOT EXISTS review_schedules (
    id BIGSERIAL PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    n_index INT NOT NULL DEFAULT 0,                              -- Expoente n (0 a 13)
    interval_days INT NOT NULL DEFAULT 1,                        -- 2^n dias
    repetition_count INT NOT NULL DEFAULT 0,                     -- Total de repetições
    consecutive_correct INT NOT NULL DEFAULT 0,                  -- Acertos seguidos
    last_reviewed_at TIMESTAMP WITH TIME ZONE,
    next_due_date DATE NOT NULL DEFAULT CURRENT_DATE,            -- Próxima data de disparo
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',               -- PENDING, NOTIFIED, COMPLETED, OVERDUE
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_student_question_schedule UNIQUE (student_id, question_id)
);

-- 7. Tabela de Logs de Interações do Chatbot (WhatsApp / UaiZap)
CREATE TABLE IF NOT EXISTS interaction_logs (
    id BIGSERIAL PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    review_schedule_id BIGINT REFERENCES review_schedules(id) ON DELETE SET NULL,
    raw_message TEXT NOT NULL,
    selected_option VARCHAR(10),
    is_correct BOOLEAN NOT NULL,
    response_time_seconds INT,
    answered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Índices para alta performance de consulta no Scheduler e Webhooks
CREATE INDEX IF NOT EXISTS idx_students_phone ON students(phone_number);
CREATE INDEX IF NOT EXISTS idx_review_schedules_due_status ON review_schedules(next_due_date, status);
CREATE INDEX IF NOT EXISTS idx_questions_topic ON questions(topic_id);
