# Relatório Técnico: Refatoração do núcleo financeiro da SaaS Billing Co.

Implantação e Entrega de Software, 2026/2, UFG/INF.
Dupla: Vinícius Prates Araújo (matrícula 201910912)

Código analisado: `SubscriptionBillingService` (commit `3dfd8dd`, arquivo original sem alterações).
Todas as referências de linha da seção 1 apontam para esse commit.

## Como reproduzir

```bash
mvn test                                  # Java 21 + Maven; nenhum banco ou SMTP necessário
git log --oneline --reverse               # os 4 passos da refatoração
git show 3dfd8dd:src/main/java/com/saas/billing/SubscriptionBillingService.java   # legado
```

## 1. Diagnóstico do débito técnico

| # | Linhas (legado) | Code smell / princípio violado | Juros (impacto em manutenção e entrega contínua) |
|---|---|---|---|
| 1 | 18–105 (88 linhas, complexidade ciclomática 18) | **Long Method** e violação do **SRP**: um único método calcula preço, consulta cliente, cobra o cartão, grava fatura e monta e-mail | Qualquer mudança de regra de preço exige reler e retestar cobrança, banco e e-mail juntos. Impossível revisar ou testar uma regra isolada. Conflitos de merge frequentes, porque todo time mexe no mesmo método |
| 2 | 18–20, 24, 27–57 | **Primitive Obsession** e **Long Parameter List**: 8 parâmetros soltos, dinheiro em `double`, data como `String`, desconto por `String promoCode` | `double` acumula erro de ponto flutuante e nunca é arredondado (uma ONG com plano de 19,90 gera `R$ 13.929999999999998` no log e no corpo do e-mail). A data inválida só falha em runtime. Trocar dois `String` na chamada compila e cobra errado, sem nenhum aviso |
| 3 | 59–63, 82–83, 89–95 | **Dependência direta de I/O** (viola DIP): `DriverManager`, `PreparedStatement` e `SimpleEmail` instanciados dentro da regra de negócio | O método não roda sem Postgres e SMTP. Não há teste unitário possível, então o CI não consegue validar a regra de cobrança. Trocar o provedor de e-mail ou de pagamento exige reescrever a regra |
| 4 | 25, 42 | **Dependência de estado global / não determinismo**: `LocalDate.now()` | A promo `BLACKFRIDAY` só vale em novembro, então o resultado do teste depende do dia em que ele roda. Bug de data não é reproduzível. Teste passa em outubro e quebra em novembro |
| 5 | 100–102 | **Exceção engolida** (`catch (Exception e)`) | Falha de banco, bug de NPE e erro de e-mail viram a mesma string em uma lista. Sem stack trace, sem alerta, sem log estruturado. O chamador não distingue "cliente inativo" de "banco caiu". Incidente em produção só é descoberto pelo cliente |
| 6 | 14–16 | **Segredos no código-fonte** (`admin` / `admin123`) | Credencial versionada no Git e igual em todo ambiente. Trocar a senha exige novo build e deploy. Risco de segurança e de auditoria |
| 7 | 31–52, 69, 75, 86 | **Magic numbers e magic strings** (`0.15`, `24`, `30.0`, `"ACTIVE"`, `"tok_"`, `"PAID"`) | A regra de negócio fica espalhada em literais sem nome. Mudar um percentual exige caçar números no método. Erro de digitação em string de status não é detectado pelo compilador |
| 8 | 96 e 98 | **Código morto e log enganoso**: `// emailSender.send();` está comentado, mas o log diz `E-mail disparado` | O log afirma uma ação que nunca acontece. Quem opera o sistema acredita que o recibo foi enviado. Bug de produção invisível (ver decisão D3) |
| 9 | 61–63, 83 | **Vazamento de recursos**: `PreparedStatement` e `ResultSet` nunca fechados | Cada cobrança deixa cursores abertos até o fim da conexão. Sob carga de fim de mês, esgota recursos do banco |
| 10 | 65, 105 | **Falha silenciosa**: sem `else` para cliente inexistente, retorna lista vazia | Cobrança de id errado não gera registro nem erro. Mantido por compatibilidade (ver decisão D4), mas é dívida a ser tratada com o negócio |
| 11 | 18 | **Parâmetro morto**: `planType` nunca é lido | Assinatura promete uma regra por plano que não existe. Quem lê a API assume um comportamento falso |

## 2. Refatorações aplicadas

Operações do catálogo de Fowler, aplicadas em passos pequenos, com a suíte executada a cada etapa.
Nesta execução as edições foram feitas manualmente (sem atalhos de IDE); os equivalentes no IntelliJ estão na coluna da direita.

| Commit | Operação | Resultado | Atalho equivalente (IntelliJ) |
|---|---|---|---|
| `46420d3` | **Replace Primitive with Object** | `Money` (record imutável, `BigDecimal`, 2 casas, `HALF_UP`) concentra todo arredondamento e as operações `plus`, `minus`, `times` | Refactor > Replace Constructor with Builder / Extract Class |
| `46420d3` | **Introduce Parameter Object** | `BillingCommand` substitui os 8 parâmetros; a data passa de `String` para `LocalDate` | Refactor > Introduce Parameter Object (Ctrl+Alt+P) |
| `46420d3` | **Extract Method** | `calculatePrice` separado do fluxo de cobrança | Ctrl+Alt+M |
| `cec4dc9` | **Extract Interface** + **Dependency Inversion** | Portas `CustomerRepository`, `InvoiceRepository`, `PaymentGateway`, `NotificationService`; adaptadores JDBC, SMTP e gateway simulado | Refactor > Extract Interface |
| `cec4dc9` | **Parameterize Dependency** | `java.time.Clock` injetado; `LocalDate.now()` sai do domínio | Refactor > Introduce Field / Change Signature |
| `cec4dc9` | **Remove Hardcoded Secret** | `BillingServiceFactory` (raiz de composição) lê credenciais de variáveis de ambiente e falha rápido se faltar alguma | n/a |
| `HEAD` (commit 4) | **Extract Class** | `PricingCalculator`, classe de domínio pura (sem I/O e sem relógio próprio) | Refactor > Extract Delegate |
| `HEAD` (commit 4) | **Replace Magic Number with Symbolic Constant** | Percentuais, limites de meses e códigos promocionais viram constantes nomeadas | Ctrl+Alt+C |
| `HEAD` (commit 4) | **Decompose Conditional** | `applyLoyaltyDiscount`, `applyNonProfitDiscount`, `applyPromo`, `applyLateFees` | Ctrl+Alt+M |
| `HEAD` (commit 4) | **Replace Generic Catch with Specific Exception** | `catch (Exception e)` vira `catch (BillingInfrastructureException e)` com log do stack trace | n/a |

## 3. Métricas antes e depois

| Métrica | Antes (`3dfd8dd`) | Depois (HEAD) | Como foi medido |
|---|---|---|---|
| Linhas não vazias do método orquestrador | 71 | 12 (`processMonthlyBilling`) + 12 (`bill`) | `grep -c` sobre o corpo do método |
| Complexidade ciclomática (McCabe) do método principal | 18 | 2 (`processMonthlyBilling`) | Contagem manual de decisões (`if`, `&&`, `?:`, `catch`) |
| Maior complexidade ciclomática em qualquer método | 18 | 6 (`PricingCalculator.applyPromo`) | Idem |
| Parâmetros do método principal | 8 | 1 (`BillingCommand`) | Assinatura |
| Imports de infraestrutura no serviço (`java.sql`, `commons-mail`) | 5 | 0 | `grep import` |
| Credenciais no código-fonte | 3 constantes | 0 | Inspeção |
| Chamadas a `LocalDate.now()` no domínio | 1 | 0 | `grep` |
| Blocos `catch (Exception e)` | 1 | 0 | `grep` |
| Classes de produção | 1 | 16 | `find src/main` |
| Testes automatizados | 0 | 49 (7 classes) | `mvn test` |
| Roda sem banco ou SMTP | Não | Sim (toda a suíte, ~0,2 s de testes) | `mvn test` |
| Cobertura de linhas (JaCoCo) | 0% | 85,5% (124/145 linhas) | `target/site/jacoco` |
| Cobertura de ramos (JaCoCo) | 0% | 95,2% | idem |
| Cobertura de linhas fora dos adaptadores JDBC | n/a | 98,4% (124/126) | idem |
| Desvio máximo de preço frente à fórmula legada | n/a | R$ 0,0124 em 8.960 combinações (7.978 idênticas ao centavo) | `LegacyPricingParityTest` |

## 4. Decisões e desvios de comportamento (leia antes de avaliar)

A restrição era não quebrar comportamento de negócio. Estes são os pontos em que a refatoração decidiu ou alterou algo, e por quê.

- **D1. Arredondamento (único desvio numérico).** O legado usa `double` e nunca arredonda. O `Money` arredonda a cada operação para centavos (`HALF_UP`), como pede o enunciado ("encapsular arredondamentos"). Consequência: o valor pode diferir em até um centavo do legado, e o texto do log muda de `R$ 90.0` para `R$ 90.00`. O desvio máximo medido é R$ 0,0124; a diferença acontece porque cada passo arredonda, em vez de arredondar só no fim.
- **D2. Escopo do `catch`.** O legado capturava qualquer `Exception`. Agora só `BillingInfrastructureException` (falha de banco, gateway ou SMTP) é capturada, registrada com stack trace via `System.Logger` e devolvida no log com o mesmo texto `Erro fatal no processamento: ...`. Um bug de programação (por exemplo `NullPointerException`) agora propaga, em vez de virar uma linha de log. Isso é intencional (item 4 do enunciado) e está coberto por teste.
- **D3. E-mail nunca enviado.** No legado o `send()` está comentado e o log diz que o e-mail foi disparado. Foi preservado: `SmtpNotificationService` monta e valida a mensagem mas não a transmite. Habilitar o envio muda comportamento de negócio (clientes passariam a receber e-mails) e precisa de decisão do produto.
- **D4. Cliente inexistente.** Continua devolvendo lista vazia, sem fatura e sem e-mail, como no legado, mesmo sendo uma falha silenciosa.
- **D5. `planType`.** Mantido em `BillingCommand` porque faz parte da entrada original, mas continua sem efeito no cálculo, como no legado.
- **D6. Conexão por operação.** O legado usava uma conexão para o fluxo todo; os adaptadores abrem uma por operação via `DataSource`. Não havia transação explícita no legado (autocommit), então a semântica de gravação é a mesma.
- **D7. Fatura nova para pagamento recusado.** Preservado: pagamento recusado ainda grava fatura `FAILED` e ainda dispara a notificação.

## 5. Limitações

- O código legado não foi executado (exige Postgres). A equivalência foi provada contra uma **cópia literal da fórmula legada** (`LegacyPricingParityTest`), não contra a classe original rodando.
- Os adaptadores JDBC (`JdbcCustomerRepository`, `JdbcInvoiceRepository`) têm 0% de cobertura: testá-los exige banco, o que o enunciado proíbe na esteira de CI. O caminho natural é um teste de integração separado, com Testcontainers, fora do `mvn test` padrão.
- A complexidade ciclomática foi contada manualmente, não por ferramenta.
- O gateway de pagamento continua simulado (aceita tokens `tok_*`), como no legado.
