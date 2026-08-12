-- =============================================================================
-- Migration: V4__add_student_ra_and_unipam_integration.sql
-- Descrição: Identificação de Estudantes por RA e Registros Acadêmicos UNIPAM
-- =============================================================================

-- 1. Adiciona coluna 'ra' na tabela 'students'
ALTER TABLE students ADD COLUMN IF NOT EXISTS ra VARCHAR(20);

-- Preenche RAs únicos temporários para registros pré-existentes caso haja algum
UPDATE students
SET ra = 'UNIPAM' || SUBSTRING(REPLACE(id::text, '-', ''), 1, 8)
WHERE ra IS NULL;

-- Garante constraint UNIQUE na coluna ra
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_students_ra'
    ) THEN
        ALTER TABLE students ADD CONSTRAINT uk_students_ra UNIQUE (ra);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_students_ra ON students(ra);

-- 2. Tabela de Registros Acadêmicos Institucionais UNIPAM (Base Central da Instituição)
CREATE TABLE IF NOT EXISTS unipam_academic_records (
    id BIGSERIAL PRIMARY KEY,
    ra VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    academic_period INT NOT NULL DEFAULT 1,
    email VARCHAR(150),
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_unipam_records_phone ON unipam_academic_records(phone_number);
CREATE INDEX IF NOT EXISTS idx_unipam_records_ra ON unipam_academic_records(ra);

-- 3. Carga Inicial de Registros Acadêmicos (Alunos UNIPAM para Demonstração e Testes)
INSERT INTO unipam_academic_records (ra, full_name, phone_number, course_id, academic_period, email, active)
VALUES
(
    '23000388',
    'Níckolas Tavares do Nascimento',
    '5534999999999',
    (SELECT id FROM courses WHERE code = 'MEDICINA'),
    9,
    'nickolas.tavares@unipam.edu.br',
    TRUE
),
(
    '23000101',
    'Mariana Silva Rocha',
    '5534988881111',
    (SELECT id FROM courses WHERE code = 'MEDICINA'),
    10,
    'mariana.rocha@unipam.edu.br',
    TRUE
),
(
    '23000202',
    'Lucas Gabriel Ferreira',
    '5534988882222',
    (SELECT id FROM courses WHERE code = 'DIREITO'),
    6,
    'lucas.ferreira@unipam.edu.br',
    TRUE
),
(
    '23000303',
    'Beatriz Albuquerque Costa',
    '5534988883333',
    (SELECT id FROM courses WHERE code = 'ENGENHARIA_SOFTWARE'),
    8,
    'beatriz.costa@unipam.edu.br',
    TRUE
),
(
    '23000404',
    'Camila Santos Moreira',
    '5534988884444',
    (SELECT id FROM courses WHERE code = 'ENFERMAGEM'),
    5,
    'camila.moreira@unipam.edu.br',
    TRUE
)
ON CONFLICT (ra) DO NOTHING;
