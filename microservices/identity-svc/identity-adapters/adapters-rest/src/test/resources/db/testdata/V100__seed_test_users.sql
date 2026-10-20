-- Test-only credentials. Loaded only when the test Flyway location includes
-- classpath:db/testdata; never packaged in the service jar.
-- Passwords: test-admin=admin-password, test-user=test-password, test-viewer=viewer-password.
INSERT INTO user_logins (user_login_id, current_password, roles) VALUES
    ('test-admin',  '{PBKDF2-SHA256}10000$dGVzdHNhbHQ=$niG5962/4NfJ74dcimSEl1SVHWKAbKjV4IFi63u37aQ=', 'ADMIN'),
    ('test-user',   '{PBKDF2-SHA256}10000$dGVzdHNhbHQ=$x0nPrTYVVaKd1Y70oTIvgB3W2shyi792oM5QIVXv+Ww=', 'USER'),
    ('test-viewer', '{PBKDF2-SHA256}10000$dGVzdHNhbHQ=$xGwhlRwDsJxmfVUYU6FygBtAN5r3QY8u6gO6Hk5Y4Ns=', 'USER');
