# fitmais-notification-service

Serviço de notificações do ecossistema **FitMais**, responsável por enviar e-mails transacionais (boas-vindas, por enquanto) de forma assíncrona.

Este serviço **não expõe API para o front-end nem para outros serviços consumirem diretamente**. Ele apenas escuta eventos publicados no RabbitMQ. Quem publica esses eventos é a aplicação principal **fitmais** (outro repositório) — sem ela rodando e publicando na exchange correta, este serviço não recebe nenhuma mensagem.

## Como funciona

1. A aplicação **fitmais** publica um evento `UsuarioRegistradoEvent` (usuário criado) na exchange `usuario.exchange`, com routing key `usuario.criado`.
2. Este serviço consome a fila `usuario.criado.queue` ([UsuarioRegistradoConsumer](src/main/java/br/com/fitmais_notification/consumer/UsuarioRegistradoConsumer.java)) e dispara o e-mail de boas-vindas via [EmailService](src/main/java/br/com/fitmais_notification/service/EmailService.java), usando o template Thymeleaf [email-boas-vindas.html](src/main/resources/templates/email-boas-vindas.html).
3. Se o processamento falhar, a mensagem é retentada automaticamente (3 tentativas, com backoff exponencial). Se todas as tentativas falharem, a mensagem é republicada na fila `usuario.criado.dlq` com o erro original anexado nos headers, e logada pelo [UsuarioRegistradoDlqConsumer](src/main/java/br/com/fitmais_notification/consumer/UsuarioRegistradoDlqConsumer.java).

Toda a topologia (exchange, fila, DLQ, binding, retry) é declarada em [RabbitMQConfig](src/main/java/br/com/fitmais_notification/config/RabbitMQConfig.java).

### Contrato do evento (`UsuarioRegistradoEvent`)

```java
record UsuarioRegistradoEvent(Long usuarioId, String username, String email, LocalDateTime registradoEm)
```

Esse formato precisa ser mantido em sincronia com o que a aplicação **fitmais** publica (serializado como JSON via Jackson).

## Stack

- Java 17 + Spring Boot 3.4.2
- Spring AMQP (RabbitMQ) para consumo de eventos
- Spring Mail + Thymeleaf para montagem e envio de e-mails
- Spring Retry para as tentativas antes do dead-letter

## Rodando localmente

### Pré-requisitos

- Java 17
- Maven
- Docker (para subir o RabbitMQ) — ou uma instância própria já rodando
- Uma conta de e-mail com senha de app (o projeto usa SMTP do Gmail por padrão)
- A aplicação **fitmais** rodando e publicando eventos, caso você queira ver o fluxo completo ponta a ponta

### 1. Subir o RabbitMQ

```bash
docker compose up -d
```

Isso sobe o RabbitMQ (usuário/senha `guest`/`guest`) com o painel de management em `http://localhost:15672`.

### 2. Configurar variáveis de ambiente

Crie um `.env` na raiz do projeto (o `spring-dotenv` carrega automaticamente):

```
MAIL_USERNAME=seu-email@gmail.com
MAIL_PASSWORD=sua-senha-de-app
```

Demais configurações (host/porta/usuário/senha do RabbitMQ, URL do front-end) têm valores padrão em [application.properties](src/main/resources/application.properties) e podem ser sobrescritas via variável de ambiente (`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`) se o RabbitMQ não estiver rodando localmente na porta padrão.

### 3. Rodar a aplicação

```bash
./mvnw spring-boot:run
```

A aplicação sobe na porta `8081`.

## Observações

- O `UsuarioRegistradoConsumer` está atualmente lançando uma exceção de teste (`Erro de teste`) no lugar do envio real de e-mail, propositalmente, para validar o fluxo de retry/DLQ. O código de envio real está comentado logo abaixo, pronto pra ser reativado.
- Como este serviço depende inteiramente de mensagens publicadas por outro projeto, não há como testá-lo isoladamente além de publicar mensagens manualmente na exchange `usuario.exchange` (pelo painel do RabbitMQ, por exemplo) no formato do `UsuarioRegistradoEvent`.
