# fitmais-notification-service

Serviço de notificações do ecossistema **FitMais**, responsável por enviar e-mails transacionais (boas-vindas, por enquanto) de forma assíncrona.

Este serviço **não expõe API para o front-end nem para outros serviços consumirem diretamente**. Ele apenas escuta eventos publicados no RabbitMQ. Quem publica esses eventos é a aplicação principal **fitmais** (repositório referenciado), sem ela rodando e publicando na exchange correta, este serviço não recebe nenhuma mensagem.

## Como funciona

1. A aplicação **fitmais** publica um evento `UsuarioRegistradoEvent` (usuário criado) na exchange `usuario.exchange`, com routing key `usuario.criado`.
2. Este serviço consome a fila `usuario.criado.queue` e dispara o e-mail de boas-vindas via EmailService, usando o template Thymeleaf 'email-boas-vindas.html'.
3. Se o processamento falhar, a mensagem é retentada automaticamente (3 tentativas). Se todas as tentativas falharem, a mensagem é republicada na fila `usuario.criado.dlq` com o erro original anexado nos headers, e logada pelo UsuarioRegistradoDlqConsumer.

### Contrato do evento (`UsuarioRegistradoEvent`)

```java
record UsuarioRegistradoEvent(Long usuarioId, String username, String email, LocalDateTime registradoEm)
```

Esse formato precisa ser mantido em sincronia com o que a aplicação **fitmais** publica (serializado como JSON).

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

Este repositório **não tem um `docker-compose.yml` próprio** (o compose com MySQL + RabbitMQ + as duas aplicações vive no [fitmais-infra](https://github.com/felipefzm/fitmais-infra)). Se você não tiver um RabbitMQ acessível (local ou via infra), suba um manualmente:

```bash
docker run -d --name fitmais-rabbitmq -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3-management
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

A aplicação sobe na porta `8081` — mas, como explicado acima, ela não expõe nenhum endpoint: nada acontece até que uma mensagem chegue na fila `usuario.criado.queue`.

## Rodando com Docker (apenas com o Dockerfile deste projeto)

Para rodar o serviço dentro de um container, sem o compose do fitmais-infra:

1. Suba o RabbitMQ (veja o passo 1 acima, se ainda não tiver um rodando).

2. Gere a imagem a partir do `Dockerfile` deste projeto:

   ```bash
   docker build -t fitmais-notification-service .
   ```

3. Rode o container, passando a configuração via variáveis de ambiente:

   ```bash
   docker run --name fitmais-notification-service -p 8081:8081 \
     -e RABBITMQ_HOST=host.docker.internal \
     -e RABBITMQ_PORT=5672 \
     -e RABBITMQ_USERNAME=guest \
     -e RABBITMQ_PASSWORD=guest \
     -e MAIL_USERNAME=seu-email@gmail.com \
     -e MAIL_PASSWORD=sua-senha-de-app \
     fitmais-notification-service
   ```

   > No Linux, `host.docker.internal` não funciona por padrão — adicione `--add-host=host.docker.internal:host-gateway` ao comando acima.

   Sozinho — sem a `fitmais-api` publicando eventos — este container sobe e fica ocioso, só escutando a fila. Para ver o fluxo completo, publique uma mensagem manualmente na exchange `usuario.exchange` (routing key `usuario.criado`) pelo painel do RabbitMQ, no formato do `UsuarioRegistradoEvent`, ou rode a `fitmais-api` também.

## Observações

- O `UsuarioRegistradoConsumer` está comentado atualmente, ele lança uma exceção de teste (`Erro de teste`) no lugar do envio real de e-mail, propositalmente, para validar o fluxo de retry/DLQ.
- Como este serviço depende inteiramente de mensagens publicadas por outro projeto, não há como testá-lo isoladamente além de publicar mensagens manualmente na exchange `usuario.exchange` (pelo painel do RabbitMQ, por exemplo) no formato do `UsuarioRegistradoEvent`.
