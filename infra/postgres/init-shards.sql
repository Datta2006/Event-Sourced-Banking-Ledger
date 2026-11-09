-- Three physical regional shard databases on the local Postgres instance.
-- The Account Command ShardRouter also creates these on demand; this script
-- makes a fresh compose volume deterministic for the demo.
CREATE DATABASE ddbs_apac;
CREATE DATABASE ddbs_eu;
CREATE DATABASE ddbs_amer;
