# 🎓 Workspace: Projeto TCC (Contexto Pessoal)

Este projeto pertence ao contexto **Pessoal / Acadêmico**.

## 🧠 Configuração do Obsidian-First para este Workspace
- **Cofre Ativo**: `C:\Users\InfoGames\OneDrive\Documentos\Obsidian - Pessoal`
- **Índice**: `C:\Users\InfoGames\OneDrive\Documentos\Obsidian - Pessoal\INDEX.md`
- **Neurônios**: `C:\Users\InfoGames\OneDrive\Documentos\Obsidian - Pessoal\Neuronios\`
- **Neurônio Principal deste Projeto**: `[[Projeto_TCC]]`

Todas as memórias, regras de negócio do TCC e documentações geradas para este projeto devem ser lidas e gravadas exclusivamente no **Cofre Pessoal**.

---

## 🌿 Governança Git & Workflow Obrigatório para Agentes

A branch `main` possui **Branch Protection Rules** ativas. Agentes **NUNCA** devem tentar fazer push direto na `main`.

### 1. Fluxo de Desenvolvimento (Git Flow):
1. **Nova Tarefa**: Sempre criar e mudar para uma branch descritiva a partir da `main`:
   - `feat/nome-da-funcionalidade`
   - `fix/nome-do-bug`
   - `docs/nome-da-documentacao`
   - `refactor/nome-da-refatoracao`
   - `chore/nome-da-tarefa`
2. **Ciclo TDD**: Escrever testes unitários primeiro (smoke/fumaça e unitários), validar falha (Red), implementar (Green) e refatorar (Refactor).
3. **Commit Semântico (Conventional Commits)**:
   - Todo commit deve seguir o padrão: `tipo(escopo): descrição em minúsculas`
   - Exemplos:
     - `feat(engine): ajusta calculo do intervalo binario 2^n`
     - `fix(uaizap): trata timeout de conexao no webhook`
     - `test(scheduler): adiciona testes do cron de disparos`
     - `docs(readme): atualiza guia de execucao do docker`
4. **Push & Pull Request**:
   - Enviar a branch: `git push -u origin <nome-da-branch>`
   - As alterações só chegam à `main` após PR aprovado com CI/CD (`Java CI with Maven`) passando 100% verde.

