-- Contas com perfil (cliente ou administrador), pedidos vinculados ao cliente
-- e exclusão lógica de produtos (pedidos antigos continuam apontando para eles).
CREATE SEQUENCE users_seq START WITH 1;
CREATE SEQUENCE products_seq START WITH 100;

CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    phone VARCHAR(20),
    address VARCHAR(200),
    city VARCHAR(100),
    zip_code VARCHAR(9),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE products ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE orders ADD COLUMN user_id BIGINT;
ALTER TABLE orders ADD CONSTRAINT orders_user_fk FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX orders_user_idx ON orders(user_id);
