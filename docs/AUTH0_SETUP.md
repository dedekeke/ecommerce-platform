# Auth0 Setup Guide

> Back to [README](../README.md).

This guide will walk you through setting up Auth0 for the E-Commerce Platform.

## Prerequisites

- An Auth0 account (free tier is sufficient for development)
- Access to https://manage.auth0.com

## Step 1: Create Auth0 Tenant

1. Log in to your Auth0 Dashboard at https://manage.auth0.com
2. If you don't have a tenant yet, create one
3. Note your tenant domain (e.g., `your-tenant.auth0.com`)

## Step 2: Create an API

The API represents your backend microservices.

1. Go to **Applications** → **APIs** in the Auth0 Dashboard
2. Click **Create API**
3. Fill in the details:
   - **Name**: `E-Commerce Platform API`
   - **Identifier**: `https://api.ecommerce-platform.com` (this is your audience)
   - **Signing Algorithm**: `RS256`
4. Click **Create**
5. Go to the **Permissions** tab and add the following scopes:
   - `read:products` - Read product information
   - `write:products` - Create/update products
   - `read:orders` - Read order information
   - `write:orders` - Create/update orders
   - `read:cart` - Read shopping cart
   - `write:cart` - Modify shopping cart
   - `admin` - Full administrative access
   - `read:profile` - Read user profile
   - `write:profile` - Update user profile

## Step 3: Create SPA Application (Frontend)

This represents your React/Angular frontend applications.

1. Go to **Applications** → **Applications**
2. Click **Create Application**
3. Fill in the details:
   - **Name**: `E-Commerce Frontend`
   - **Application Type**: Select **Single Page Web Applications**
4. Click **Create**
5. Go to the **Settings** tab and configure:
   - **Allowed Callback URLs**:
     ```
     http://localhost:3000/callback,
     http://localhost:5000/callback,
     http://localhost:4200/callback
     ```
   - **Allowed Logout URLs**:
     ```
     http://localhost:3000,
     http://localhost:5000,
     http://localhost:4200
     ```
   - **Allowed Web Origins**:
     ```
     http://localhost:3000,
     http://localhost:5000,
     http://localhost:4200
     ```
   - **Allowed Origins (CORS)**:
     ```
     http://localhost:3000,
     http://localhost:5000,
     http://localhost:4200
     ```
6. Scroll down and click **Save Changes**
7. Copy the **Client ID** - you'll need this for your `.env` file

## Step 4: Create Machine-to-Machine Application (Optional)

This is for service-to-service communication.

1. Go to **Applications** → **Applications**
2. Click **Create Application**
3. Fill in the details:
   - **Name**: `E-Commerce M2M`
   - **Application Type**: Select **Machine to Machine Applications**
4. Select the API you created earlier (`E-Commerce Platform API`)
5. Authorize all permissions
6. Click **Create**
7. Copy the **Client ID** and **Client Secret** for M2M communication

## Step 5: Configure User Roles (Optional but Recommended)

1. Go to **User Management** → **Roles**
2. Create the following roles:
   - **Customer**: Regular customer role
     - Permissions: `read:products`, `read:cart`, `write:cart`, `read:orders`, `write:orders`, `read:profile`, `write:profile`
   - **Admin**: Administrator role
     - Permissions: All permissions including `admin`, `write:products`

## Step 6: Configure Rules for Token Customization (Optional)

To add custom claims to JWT tokens:

1. Go to **Auth Pipeline** → **Rules**
2. Click **Create Rule**
3. Select **Empty rule**
4. Add the following rule to include user roles in the token:

```javascript
function addRolesToToken(user, context, callback) {
  const namespace = 'https://api.ecommerce-platform.com';
  const assignedRoles = (context.authorization || {}).roles;

  let idTokenClaims = context.idToken || {};
  let accessTokenClaims = context.accessToken || {};

  idTokenClaims[`${namespace}/roles`] = assignedRoles;
  accessTokenClaims[`${namespace}/roles`] = assignedRoles;

  context.idToken = idTokenClaims;
  context.accessToken = accessTokenClaims;

  callback(null, user, context);
}
```

5. Save the rule

## Step 7: Update Your .env File

Copy `.env.template` to `.env` and update the following values:

```bash
# Auth0 Configuration
AUTH0_DOMAIN=your-tenant.auth0.com
AUTH0_ISSUER_URI=https://your-tenant.auth0.com/
AUTH0_CLIENT_ID=your_spa_client_id_from_step_3
AUTH0_CLIENT_SECRET=your_spa_client_secret  # May be empty for SPA
AUTH0_AUDIENCE=https://api.ecommerce-platform.com
AUTH0_M2M_CLIENT_ID=your_m2m_client_id_from_step_4
AUTH0_M2M_CLIENT_SECRET=your_m2m_client_secret_from_step_4
```

## Step 8: Test Authentication

### Test with cURL

Get a test token using the Auth0 test console or create a simple test:

```bash
# Get a token (replace with your values)
curl --request POST \
  --url https://your-tenant.auth0.com/oauth/token \
  --header 'content-type: application/json' \
  --data '{
    "client_id":"YOUR_CLIENT_ID",
    "client_secret":"YOUR_CLIENT_SECRET",
    "audience":"https://api.ecommerce-platform.com",
    "grant_type":"client_credentials"
  }'
```

### Test API Gateway

Once you have a token, test the API Gateway:

```bash
# Test without token (should fail)
curl http://localhost:8080/api/users/me

# Test with token (should succeed)
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/api/users/me
```

## Security Best Practices

1. **Never commit** your `.env` file to version control
2. Use **different tenants** for development, staging, and production
3. Rotate **client secrets** regularly in production
4. Enable **MFA** for admin accounts
5. Review **Auth0 logs** regularly for suspicious activity
6. Use **refresh tokens** for long-lived sessions
7. Implement **token revocation** for logout functionality

## Troubleshooting

### "Invalid audience" error
- Verify that `AUTH0_AUDIENCE` in `.env` matches the API identifier in Auth0
- Check that the frontend is requesting the correct audience when getting tokens

### "Invalid issuer" error
- Verify that `AUTH0_ISSUER_URI` ends with a trailing slash: `https://your-tenant.auth0.com/`
- Check that the domain matches your Auth0 tenant

### CORS errors
- Ensure all frontend URLs are added to "Allowed Origins (CORS)" in the SPA application settings
- Verify that the API Gateway CORS configuration includes your frontend URLs

### Token not being relayed to services
- Check that `TokenRelay` filter is configured in the gateway routes
- Verify that downstream services are configured to accept JWT tokens

## References

- [Auth0 Documentation](https://auth0.com/docs)
- [Auth0 SPA Quickstart](https://auth0.com/docs/quickstart/spa)
- [Auth0 API Authorization](https://auth0.com/docs/authorization)
- [JWT.io - Decode and verify JWT tokens](https://jwt.io)
