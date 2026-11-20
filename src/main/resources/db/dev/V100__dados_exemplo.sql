-- ============================================================================
-- V100: dados de exemplo (somente desenvolvimento e testes)
-- Senha de todos os usuários: senha123
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO departamento (nome, sigla, email_contato) VALUES
                                                          ('Tecnologia da Informação',   'TI',    'ti.spo@ifsp.edu.br'),
                                                          ('Secretaria Acadêmica',       'SEC',   'secretaria.spo@ifsp.edu.br'),
                                                          ('Coordenação de Curso',       'COORD', 'coord.bsi@ifsp.edu.br'),
                                                          ('Manutenção e Infraestrutura','INFRA', 'infra.spo@ifsp.edu.br');

INSERT INTO usuario (nome, email, senha_hash, papel, id_departamento) VALUES
                                                                          ('Ewerton Godinho Monteiro',      'ewerton.monteiro@aluno.ifsp.edu.br', crypt('senha123', gen_salt('bf', 10)), 'ALUNO',     NULL),
                                                                          ('Professor de Exemplo',          'professor.exemplo@ifsp.edu.br',      crypt('senha123', gen_salt('bf', 10)), 'PROFESSOR', NULL),
                                                                          ('Dennis Daigo Olher',            'dennis.olher@ifsp.edu.br',           crypt('senha123', gen_salt('bf', 10)), 'TECNICO',   (SELECT id_departamento FROM departamento WHERE sigla = 'TI')),
                                                                          ('Lucas Eduardo de Araujo Silva', 'lucas.silva@ifsp.edu.br',            crypt('senha123', gen_salt('bf', 10)), 'TECNICO',   (SELECT id_departamento FROM departamento WHERE sigla = 'SEC')),
                                                                          ('Técnico de Infraestrutura',     'tecnico.infra@ifsp.edu.br',          crypt('senha123', gen_salt('bf', 10)), 'TECNICO',   (SELECT id_departamento FROM departamento WHERE sigla = 'INFRA')),
                                                                          ('Thúlio Souza Alves',            'thulio.alves@ifsp.edu.br',           crypt('senha123', gen_salt('bf', 10)), 'GESTOR',    NULL);

INSERT INTO categoria (nome, prioridade_padrao, id_departamento) VALUES
                                                                     ('Suporte técnico',        'ALTA',  (SELECT id_departamento FROM departamento WHERE sigla = 'TI')),
                                                                     ('Documentos',             'MEDIA', (SELECT id_departamento FROM departamento WHERE sigla = 'SEC')),
                                                                     ('Matrícula',              'ALTA',  (SELECT id_departamento FROM departamento WHERE sigla = 'COORD')),
                                                                     ('Atendimento acadêmico',  'BAIXA', (SELECT id_departamento FROM departamento WHERE sigla = 'COORD')),
                                                                     ('Infraestrutura',         'MEDIA', (SELECT id_departamento FROM departamento WHERE sigla = 'INFRA'));

-- Chamados em todos os status. Datas relativas a hoje.
INSERT INTO chamado (titulo, descricao, status, prioridade, data_abertura, data_atualizacao,
                     data_conclusao, observacao_atendimento, id_solicitante, id_responsavel, id_categoria)
SELECT v.titulo, v.descricao, v.status, v.prioridade, v.abertura, v.atualizacao, v.conclusao, v.obs,
       (SELECT id_usuario   FROM usuario   WHERE email = v.solicitante),
       (SELECT id_usuario   FROM usuario   WHERE email = v.responsavel),
       (SELECT id_categoria FROM categoria WHERE nome  = v.categoria)
FROM (VALUES
          ('Problema no laboratório', 'Computador da bancada 04 do laboratório 401 não liga.',
           'ABERTO', 'ALTA', CURRENT_DATE - 3, CURRENT_DATE - 3, NULL::date, NULL::text,
           'ewerton.monteiro@aluno.ifsp.edu.br', 'dennis.olher@ifsp.edu.br', 'Suporte técnico'),
          ('Rede do laboratório fora do ar', 'Sem acesso à internet em todo o laboratório 402.',
           'ABERTO', 'URGENTE', CURRENT_DATE - 1, CURRENT_DATE - 1, NULL, NULL,
           'professor.exemplo@ifsp.edu.br', 'dennis.olher@ifsp.edu.br', 'Suporte técnico'),
          ('Solicitação de declaração', 'Preciso de declaração de matrícula atualizada para estágio.',
           'EM_ANALISE', 'MEDIA', CURRENT_DATE - 2, CURRENT_DATE - 1, NULL, 'Documento em emissão.',
           'ewerton.monteiro@aluno.ifsp.edu.br', 'lucas.silva@ifsp.edu.br', 'Documentos'),
          ('Ar-condicionado com defeito', 'O ar-condicionado da sala 305 não resfria.',
           'EM_ANALISE', 'ALTA', CURRENT_DATE - 7, CURRENT_DATE - 2, NULL, 'Peça solicitada ao fornecedor.',
           'professor.exemplo@ifsp.edu.br', 'tecnico.infra@ifsp.edu.br', 'Infraestrutura'),
          ('Dúvida sobre acesso ao Wi-Fi', 'Solicitante não informou o número de prontuário para reset.',
           'AGUARDANDO_SOLICITANTE', 'BAIXA', CURRENT_DATE - 20, CURRENT_DATE - 18, NULL, NULL,
           'ewerton.monteiro@aluno.ifsp.edu.br', 'dennis.olher@ifsp.edu.br', 'Suporte técnico'),
          ('Acesso ao laboratório fora do horário', 'Aguardando justificativa e horário desejado.',
           'AGUARDANDO_SOLICITANTE', 'MEDIA', CURRENT_DATE - 6, CURRENT_DATE - 5, NULL, NULL,
           'professor.exemplo@ifsp.edu.br', 'dennis.olher@ifsp.edu.br', 'Suporte técnico'),
          ('Ajuste de matrícula', 'Solicito inclusão na unidade curricular de Banco de Dados II.',
           'CONCLUIDO', 'ALTA', CURRENT_DATE - 5, CURRENT_DATE - 1, CURRENT_DATE - 1, 'Inclusão realizada.',
           'ewerton.monteiro@aluno.ifsp.edu.br', 'thulio.alves@ifsp.edu.br', 'Matrícula'),
          ('Histórico escolar incompleto', 'Disciplina cursada não aparece no histórico.',
           'CONCLUIDO', 'MEDIA', CURRENT_DATE - 10, CURRENT_DATE - 6, CURRENT_DATE - 6, 'Histórico corrigido.',
           'ewerton.monteiro@aluno.ifsp.edu.br', 'lucas.silva@ifsp.edu.br', 'Documentos'),
          ('Troca de lâmpadas do corredor', 'Três lâmpadas queimadas no corredor do 3º andar.',
           'CONCLUIDO', 'BAIXA', CURRENT_DATE - 12, CURRENT_DATE - 9, CURRENT_DATE - 9, 'Lâmpadas trocadas.',
           'professor.exemplo@ifsp.edu.br', 'tecnico.infra@ifsp.edu.br', 'Infraestrutura'),
          ('Solicitação de trancamento duplicada', 'Pedido aberto duas vezes por engano.',
           'CANCELADO', 'BAIXA', CURRENT_DATE - 8, CURRENT_DATE - 7, CURRENT_DATE - 7, 'Duplicado do chamado anterior.',
           'ewerton.monteiro@aluno.ifsp.edu.br', 'thulio.alves@ifsp.edu.br', 'Atendimento acadêmico'),
          ('Projetor da sala 302 sem imagem', 'O projetor liga, mas não exibe imagem.',
           'ABERTO', 'MEDIA', CURRENT_DATE - 1, CURRENT_DATE - 1, NULL, NULL,
           'professor.exemplo@ifsp.edu.br', NULL, 'Infraestrutura')
     ) AS v(titulo, descricao, status, prioridade, abertura, atualizacao, conclusao, obs,
            solicitante, responsavel, categoria);