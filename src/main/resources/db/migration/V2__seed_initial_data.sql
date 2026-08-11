-- =============================================================================
-- Migration: V2__seed_initial_data.sql
-- Descrição: Carga inicial de Especialidades Médicas e Questões de Exemplo (Internato)
-- =============================================================================

-- 1. Especialidades Principais do Internato Médico
INSERT INTO specialties (code, name, description) VALUES
('CLINICA_MEDICA', 'Clínica Médica', 'Atendimento ambulatorial e enfermarias de clínica médica de adultos'),
('PEDIATRIA', 'Pediatria', 'Puericultura, urgências e emergências pediátricas'),
('CIRURGIA_GERAL', 'Cirurgia Geral', 'Rotina cirúrgica, trauma e abdome agudo'),
('GINECOLOGIA_OBSTETRICIA', 'Ginecologia e Obstetrícia', 'Pré-natal, parto, puerpério e afecções ginecológicas'),
('MEDICINA_FAMILIA_COMUNIDADE', 'Medicina de Família e Comunidade', 'Atenção primária à saúde, ESF e manejo de doenças crônicas')
ON CONFLICT (code) DO NOTHING;

-- 2. Tópicos de Amostra
INSERT INTO topics (id, specialty_id, name, summary_text) VALUES
(1, (SELECT id FROM specialties WHERE code = 'CLINICA_MEDICA'), 'Crise Hipertensiva', 'Diferenciação crucial entre Emergência Hipertensiva (com lesão aguda de órgão-alvo) e Urgência Hipertensiva (sem lesão aguda).'),
(2, (SELECT id FROM specialties WHERE code = 'PEDIATRIA'), 'Manejo de Desidratação Aguda', 'Planos de reidratação do Ministério da Saúde: Plano A (domiciliar), Plano B (SRO supervisionada), Plano C (expansão venosa).')
ON CONFLICT (id) DO NOTHING;

-- 3. Questões de Amostra
INSERT INTO questions (id, topic_id, question_type, statement, clinical_explanation, difficulty, source) VALUES
(1, 1, 'MULTIPLE_CHOICE', 
 'Paciente de 58 anos dá entrada no PS com PA 210x120 mmHg e cefaleia intensa sem sinais de encefalopatia ou lesão aguda de órgão-alvo. Qual a melhor conduta?',
 'Trata-se de uma Urgência Hipertensiva. O controle pressórico deve ser feito com anti-hipertensivo oral de ação gradual (ex: Captopril VO), evitando quedas bruscas de PA.',
 'MEDIUM', 'UNIPAM 2025/1'),
(2, 2, 'MULTIPLE_CHOICE',
 'Lactente de 8 meses apresenta diarreia líquida há 2 dias. Ao exame: olhos fundos, sinal da prega desaparece lentamente e bebe água com avidez. Qual o plano de reidratação indicado?',
 'O paciente apresenta sinais clínicos de desidratação moderada sem choque. A conduta padrão é o Plano B (terapia de reidratação oral supervisionada na unidade de saúde).',
 'EASY', 'UNIPAM 2025/1')
ON CONFLICT (id) DO NOTHING;

-- 4. Alternativas da Questão 1 (Crise Hipertensiva)
INSERT INTO question_options (question_id, letter, option_text, is_correct) VALUES
(1, 'A', 'Iniciar Nitroprussiato de Sódio IV em bomba de infusão contínua', FALSE),
(1, 'B', 'Prescrever Captopril 25mg VO e reavaliar em 60 minutos', TRUE),
(1, 'C', 'Administrar Furosemida 40mg IV de imediato', FALSE),
(1, 'D', 'Prescrever Nifedipino sublingual de ação rápida', FALSE)
ON CONFLICT (question_id, letter) DO NOTHING;

-- 5. Alternativas da Questão 2 (Desidratação Pediátrica)
INSERT INTO question_options (question_id, letter, option_text, is_correct) VALUES
(2, 'A', 'Plano A: Aumento da ingesta de líquidos e SRO domiciliar', FALSE),
(2, 'B', 'Plano B: Solução de Reidratação Oral (SRO) supervisionada na unidade', TRUE),
(2, 'C', 'Plano C: Expansão venosa rápida com Soro Fisiológico 0.9%', FALSE),
(2, 'D', 'Antibioticoterapia empírica imediata com Ciprofloxacino', FALSE)
ON CONFLICT (question_id, letter) DO NOTHING;
