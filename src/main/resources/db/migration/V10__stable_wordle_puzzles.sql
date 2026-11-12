-- Cuvântul unei zile rămâne stabil după rotirea cheilor sau actualizarea băncii de conținut.
-- Rundele vechi păstrează datele existente; o victorie se poate reconstrui din ultima încercare.
CREATE TABLE wordle_puzzles (
    game_date varchar(16) PRIMARY KEY,
    answer varchar(5) NOT NULL CHECK (answer ~ '^[A-Z]{5}$'),
    topic text NOT NULL,
    hint text NOT NULL,
    created_at timestamptz NOT NULL
);
ALTER TABLE wordle_games ADD COLUMN puzzle_date varchar(16) REFERENCES wordle_puzzles(game_date);
ALTER TABLE wordle_games ADD CONSTRAINT wordle_puzzle_date_matches CHECK (puzzle_date IS NULL OR puzzle_date = game_date);
