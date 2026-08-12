-- =============================================================================
-- Migration: V3__multi_course_dynamic_schema.sql
-- Descrição: Evolução para Arquitetura Multi-Curso e Domínio Dinâmico
-- =============================================================================

-- 1. Tabela de Cursos Universitários / Áreas de Conhecimento
CREATE TABLE IF NOT EXISTS courses (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    tutor_persona TEXT NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. Seed dos Cursos Iniciais
INSERT INTO courses (code, name, description, tutor_persona) VALUES
('MEDICINA', 'Medicina', 'Graduação em Medicina com foco no Internato e Residência Médica', 'Você é um Preceptor e Tutor Médico especialista auxiliando estudantes de Medicina no raciocínio clínico e diagnóstico.'),
('DIREITO', 'Direito', 'Graduação em Direito com foco na OAB e Concursos Jurídicos', 'Você é um Professor Jurista especialista auxiliando estudantes de Direito na interpretação doutrinária, jurisprudência e legislação.'),
('ENGENHARIA_SOFTWARE', 'Engenharia de Software', 'Graduação em Engenharia de Software e Ciência da Computação', 'Você é um Arquiteto de Software sênior e Professor de Computação auxiliando estudantes em algoritmos, padrões e engenharia de software.'),
('ENFERMAGEM', 'Enfermagem', 'Graduação em Enfermagem com foco na assistência e procedimentos clínicos', 'Você é um Preceptor de Enfermagem especialista auxiliando estudantes em semiologia, procedimentos e cuidados de saúde.')
ON CONFLICT (code) DO NOTHING;

-- 3. Adiciona course_id na tabela de Especialidades/Áreas
ALTER TABLE specialties ADD COLUMN IF NOT EXISTS course_id BIGINT REFERENCES courses(id) ON DELETE RESTRICT;

-- Atualiza especialidades existentes (Medicina) para o curso de Medicina
UPDATE specialties 
SET course_id = (SELECT id FROM courses WHERE code = 'MEDICINA')
WHERE course_id IS NULL;

-- 4. Adiciona course_id e academic_period na tabela de Estudantes
ALTER TABLE students ADD COLUMN IF NOT EXISTS course_id BIGINT REFERENCES courses(id) ON DELETE RESTRICT;
ALTER TABLE students ADD COLUMN IF NOT EXISTS academic_period INT DEFAULT 1;

-- Atualiza estudantes existentes para o curso de Medicina
UPDATE students 
SET course_id = (SELECT id FROM courses WHERE code = 'MEDICINA'),
    academic_period = COALESCE(intern_period, 9)
WHERE course_id IS NULL;

-- 5. Adiciona coluna explanation na tabela de Questões para compatibilidade multi-curso
ALTER TABLE questions ADD COLUMN IF NOT EXISTS explanation TEXT;

-- Sincroniza justificativa existente com a nova coluna
UPDATE questions 
SET explanation = clinical_explanation 
WHERE explanation IS NULL;

-- 6. Seed de Áreas, Tópicos e Questões para Direito e Engenharia de Software
-- 6.1 Disciplinas de Direito
INSERT INTO specialties (course_id, code, name, description) VALUES
((SELECT id FROM courses WHERE code = 'DIREITO'), 'DIR_CONSTITUCIONAL', 'Direito Constitucional', 'Controle de Constitucionalidade, Direitos Fundamentais e Organização do Estado'),
((SELECT id FROM courses WHERE code = 'DIREITO'), 'DIR_PENAL', 'Direito Penal', 'Teoria Geral do Crime, Penas e Crimes em Espécie')
ON CONFLICT (code) DO NOTHING;

-- 6.2 Disciplinas de Engenharia de Software
INSERT INTO specialties (course_id, code, name, description) VALUES
((SELECT id FROM courses WHERE code = 'ENGENHARIA_SOFTWARE'), 'ENG_ARQUITETURA', 'Arquitetura de Software', 'Padrões de Projeto, Microsserviços e Mensageria'),
((SELECT id FROM courses WHERE code = 'ENGENHARIA_SOFTWARE'), 'ENG_ALGORITMOS', 'Estrutura de Dados e Algoritmos', 'Complexidade Big-O, Grafos e Árvores')
ON CONFLICT (code) DO NOTHING;

-- 6.3 Tópicos de Direito
INSERT INTO topics (specialty_id, name, summary_text) VALUES
((SELECT id FROM specialties WHERE code = 'DIR_CONSTITUCIONAL'), 'Ações Constitucionais e Remédios', 'Habeas Corpus, Mandado de Segurança, Habeas Data e Ação Popular.'),
((SELECT id FROM specialties WHERE code = 'ENG_ARQUITETURA'), 'Padrões de Mensageria e Concorrência', 'Direct-to-Queue, Dead Letter Queue, AMQP e Idempotência.')
ON CONFLICT DO NOTHING;

-- 6.4 Questão de Direito
INSERT INTO questions (topic_id, question_type, statement, clinical_explanation, explanation, difficulty, source, active) VALUES
(
    (SELECT id FROM topics WHERE name = 'Ações Constitucionais e Remédios' LIMIT 1),
    'MULTIPLE_CHOICE',
    'Conforme a Constituição Federal de 1988, qual remédio constitucional é cabível para assegurar o conhecimento de informações relativas à pessoa do impetrante constantes de registros ou bancos de dados de entidades governamentais?',
    'O Habeas Data (art. 5º, LXXII, "a", da CF/88) destina-se a assegurar o conhecimento de informações pessoais do impetrante em bancos de dados públicos.',
    'O Habeas Data (art. 5º, LXXII, "a", da CF/88) destina-se a assegurar o conhecimento de informações pessoais do impetrante em bancos de dados públicos.',
    'EASY',
    'Exame de Ordem OAB / FGV',
    TRUE
) ON CONFLICT DO NOTHING;

-- Alternativas da Questão de Direito
INSERT INTO question_options (question_id, letter, option_text, is_correct)
SELECT q.id, opt.letter, opt.option_text, opt.is_correct
FROM questions q
CROSS JOIN (
    VALUES 
        ('A', 'Mandado de Segurança Individual', FALSE),
        ('B', 'Habeas Data', TRUE),
        ('C', 'Habeas Corpus', FALSE),
        ('D', 'Ação Popular', FALSE)
) AS opt(letter, option_text, is_correct)
WHERE q.statement LIKE 'Conforme a Constituição Federal de 1988, qual remédio constitucional é cabível%'
ON CONFLICT DO NOTHING;

-- 6.5 Questão de Engenharia de Software
INSERT INTO questions (topic_id, question_type, statement, clinical_explanation, explanation, difficulty, source, active) VALUES
(
    (SELECT id FROM topics WHERE name = 'Padrões de Mensageria e Concorrência' LIMIT 1),
    'MULTIPLE_CHOICE',
    'Em uma arquitetura orientada a eventos com RabbitMQ, qual é o padrão recomendado para lidar com mensagens venenosas (poison messages) que falham repetidamente após múltiplas tentativas de reprocessamento?',
    'O padrão Dead Letter Queue (DLQ) encaminha mensagens que não puderam ser processadas para uma fila secundária de análise, evitando bloqueio do consumidor principal.',
    'O padrão Dead Letter Queue (DLQ) encaminha mensagens que não puderam ser processadas para uma fila secundária de análise, evitando bloqueio do consumidor principal.',
    'MEDIUM',
    'Enterprise Integration Patterns',
    TRUE
) ON CONFLICT DO NOTHING;

-- Alternativas da Questão de Computação
INSERT INTO question_options (question_id, letter, option_text, is_correct)
SELECT q.id, opt.letter, opt.option_text, opt.is_correct
FROM questions q
CROSS JOIN (
    VALUES 
        ('A', 'Descarte silencioso imediato sem log', FALSE),
        ('B', 'Encaminhamento para Dead Letter Queue (DLQ)', TRUE),
        ('C', 'Loop infinito de retry com bloqueio do canal', FALSE),
        ('D', 'Persistência síncrona na memória RAM sem disco', FALSE)
) AS opt(letter, option_text, is_correct)
WHERE q.statement LIKE 'Em uma arquitetura orientada a eventos com RabbitMQ, qual é o padrão recomendado%'
ON CONFLICT DO NOTHING;

-- Índices adicionais para suporte multi-curso
CREATE INDEX IF NOT EXISTS idx_specialties_course ON specialties(course_id);
CREATE INDEX IF NOT EXISTS idx_students_course ON students(course_id);
