
DROP TABLE student
IF EXISTS;

CREATE TABLE student (
  id     INT NOT NULL,
  name   VARCHAR(20),
  age    INT NOT NULL
);

INSERT INTO student VALUES
  (1, 'a', 10),
  (2, 'b', 10),
  (3, 'c', 10),
  (4, 'c', 10),
  (5, 'c', 10),
  (6, 'c', 10),
  (7, 'c', 10);
