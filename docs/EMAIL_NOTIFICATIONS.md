# Email Notifications (Local Dev with MailHog)

The notification-service sends transactional emails (order confirmation,
payment receipt, shipping, promotion announcement) by reacting to Kafka
events. In local development those emails are captured by **MailHog**
instead of being sent to a real SMTP provider.

## Start MailHog
```bash
docker-compose up -d mailhog
```
- SMTP listener: `localhost:1025`
- Web UI: <http://localhost:8025>

## Trigger an order confirmation
1. Run notification-service with the `personal` profile
   (`-Dspring.profiles.active=personal`) so it points at `localhost:1025`.
2. Run order-service.
3. `POST /api/orders` with body:
   `{"userId":"u1","userEmail":"buyer@example.com","userName":"Buyer","shippingAddress":{...}}`
4. Order-service publishes `order.created`; notification-service consumes it
   and the email lands in MailHog.

## Trigger a promotion announcement
- `POST /api/promotions` on the promotion-service. It publishes
  `promotion.created`; notification-service routes it to the
  `notification.email.announcement-recipient` (default
  `announcements@ecommerce.local`).

## Templates
`services/notification-service/src/main/resources/templates/`
(`order-confirmation.html`, `payment-receipt.html`,
`shipping-notification.html`, `promotion-announcement.html`).
Seeded into MongoDB by `NotificationTemplateInitializer` on startup.

## Production switch
Set real SMTP creds in `.env` (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`,
`MAIL_PASSWORD`) and turn `notification.email.enabled=true` in the active
profile. Re-enable `mail.smtp.auth` and `starttls`.
