CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(40) NOT NULL,
    description VARCHAR(500) NOT NULL,
    price NUMERIC(12,2) NOT NULL CHECK (price > 0),
    original_price NUMERIC(12,2),
    image VARCHAR(200) NOT NULL,
    color VARCHAR(40) NOT NULL,
    badge VARCHAR(40),
    stock INTEGER NOT NULL CHECK (stock >= 0)
);
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    address VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    zip_code VARCHAR(9) NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL CHECK (subtotal > 0),
    shipping NUMERIC(12,2) NOT NULL CHECK (shipping >= 0),
    total NUMERIC(12,2) NOT NULL CHECK (total = subtotal + shipping),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_items (
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(120) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    PRIMARY KEY (order_id, product_id)
);
