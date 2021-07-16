CREATE TABLE users (
  id INT,
  username VARCHAR(255),
  password VARCHAR(255),
  role VARCHAR(255)
);

INSERT INTO users (id, username, password, role) VALUES (1, 'admin', 'admin', 'admin');
INSERT INTO users (id, username, password, role) VALUES (2, 'user',  'user', 'user');
