# Console UI Security

## API Key Authentication

The Console UI now requires API key authentication for all sensitive endpoints to prevent unauthorized access to exploit workflows, security scans, traffic generation, and data deletion operations.

### Configuration

Set the `CONSOLE_API_KEY` environment variable to enable authentication:

```bash
export CONSOLE_API_KEY="your-secure-random-key-here"
```

**Important:** 
- If `CONSOLE_API_KEY` is not set, all protected endpoints will return HTTP 503 (Service Unavailable) to deny access.
- Use a strong, randomly generated key (e.g., 32+ characters).
- Keep the API key secret and rotate it regularly.

### Using Protected Endpoints

To access protected endpoints, include the API key in the `X-API-Key` header:

```bash
curl -H "X-API-Key: your-secure-random-key-here" \
     http://console.localhost/exploit/start
```

### Protected Endpoints

The following endpoints require API key authentication:

#### Exploit Endpoints
- `POST /exploit/start` - Start full exploit workflow
- `POST /exploit/stop` - Stop exploit workflow
- `POST /exploit/clear` - Clear exploit output buffer
- `GET /exploit/xss` - XSS exploit
- `GET /exploit/login` - Login exploit
- `GET /exploit/command-injection` - Command injection exploit
- `GET /exploit/path-traversal` - Path traversal exploit
- `GET /exploit/sql-injection` - SQL injection exploit
- `GET /exploit/log4shell-sleep` - Log4Shell sleep exploit
- `GET /exploit/log4shell-cmd-exec` - Log4Shell command execution exploit
- `GET /exploit/ssjs-injection` - SSJS injection exploit
- `GET /exploit/xxe` - XXE exploit
- `GET /exploit/deserialization` - Deserialization exploit
- `GET /exploit/ssti` - SSTI exploit

#### ZAP Scan Endpoints
- `GET /zap/scan/start` - Start ZAP security scan
- `GET /zap/scan/stop` - Stop ZAP security scan
- `GET /zap/scan/clear` - Clear scan output buffer

#### Traffic Generation Endpoints
- `GET /traffic/start` - Start traffic generation
- `GET /traffic/stop` - Stop traffic generation
- `GET /traffic/clear` - Clear traffic output buffer

#### Data Deletion Endpoints
- `POST /delete/all` - Delete all incidents and issues
- `POST /delete/incident` - Delete specific incident
- `POST /delete/issue` - Delete specific issue
- `POST /delete/application` - Delete application data

### Unprotected Endpoints

The following endpoints remain publicly accessible:

#### Status/Read-Only Endpoints
- `GET /` - Home page
- `GET /deployment/health` - Health check
- `GET /zap/health` - ZAP health check
- `GET /zap/scan/status` - Scan status
- `GET /exploit/status` - Exploit status
- `GET /exploit/list` - List available exploits
- `GET /traffic/status` - Traffic generation status

#### Callback Endpoints
- `GET/POST /log4shell/callback` - Receives callbacks from exploited systems (must remain public)

### Error Responses

- **401 Unauthorized**: No API key provided in request
- **403 Forbidden**: Invalid API key provided
- **503 Service Unavailable**: API key authentication not configured (CONSOLE_API_KEY not set)

### Security Best Practices

1. **Generate Strong Keys**: Use cryptographically secure random strings
   ```bash
   # Example: Generate a 32-character random key
   openssl rand -base64 32
   ```

2. **Secure Storage**: Store the API key in Kubernetes secrets, not in code or plain environment files
   ```yaml
   apiVersion: v1
   kind: Secret
   metadata:
     name: console-api-key
   type: Opaque
   stringData:
     api-key: "your-secure-random-key-here"
   ```

3. **Environment Variable Injection**: Reference the secret in your deployment
   ```yaml
   env:
   - name: CONSOLE_API_KEY
     valueFrom:
       secretKeyRef:
         name: console-api-key
         key: api-key
   ```

4. **Key Rotation**: Regularly rotate the API key and update all clients

5. **Monitoring**: Monitor authentication failures in logs for potential security incidents

### Deployment Example

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: console-ui
spec:
  template:
    spec:
      containers:
      - name: console-ui
        image: console-ui:latest
        env:
        - name: CONSOLE_API_KEY
          valueFrom:
            secretKeyRef:
              name: console-api-key
              key: api-key
```

### Migration Guide

For existing deployments:

1. Generate a secure API key
2. Create a Kubernetes secret with the key
3. Update the console-ui deployment to include the CONSOLE_API_KEY environment variable
4. Update all clients (scripts, UI, etc.) to include the X-API-Key header
5. Verify authentication is working by testing a protected endpoint
6. Monitor logs for authentication failures

### Troubleshooting

**Problem**: Getting 503 errors on protected endpoints
- **Solution**: Ensure CONSOLE_API_KEY environment variable is set

**Problem**: Getting 401 errors
- **Solution**: Include the X-API-Key header in your requests

**Problem**: Getting 403 errors
- **Solution**: Verify the API key matches the CONSOLE_API_KEY environment variable

**Problem**: Callback endpoint not working
- **Solution**: The /log4shell/callback endpoint is intentionally unprotected and should work without authentication
