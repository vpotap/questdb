[![Codacy Badge](https://api.codacy.com/project/badge/grade/83c6250bd9fc45a98c12c191af710754)](https://www.codacy.com/app/bluestreak/nfsdb)
[![Build Status](https://semaphoreci.com/api/v1/appsicle/questdb/branches/master/badge.svg)](https://semaphoreci.com/appsicle/questdb)

QuestDB is a time series database built for non-compromising performance, data accessibility and operational simplicity. It bridges the gap between traditional relational and time series databases by providing fast SQL access to both types of data. 

Existing features and component are:

- SQL language supporting filtering, aggregation, joins, time series joins, sub-queries, analytic functions
- SQL language optimiser to help making queries declarative rather than procedural
- Build-in http server component and Web Console, supporting data import and query execution
- REST API for data import, query and export
- Data import for delimited text files (CSV, tab and pipe) with automatic data type recognition 
- Programmatic data import and query from Java programming language
- Data replication engine
- Ticker plant support with automatic failover
- Can be used as both standalone server or embedded database thanks to small library size and no external dependencies
- Minimal memory footprint, data streaming query engine and no-GC operation

## UI Screenshots


Drag-Drop bulk import
![Import Progress] (https://cloud.githubusercontent.com/assets/7276403/16665958/70eecec8-447d-11e6-8e78-1437c9c15db5.png)


Automatic format recognition 
![Data Import Summary](https://cloud.githubusercontent.com/assets/7276403/16666673/ae88722c-4480-11e6-96d3-cd309475ca9d.png)


Query editor
![Query Editor](https://cloud.githubusercontent.com/assets/7276403/16667611/5339f3fa-4485-11e6-89d3-e2c92c440bd6.png "Query Editor")

## License

QuestDB is licensed under GNU Affero General Public License (AGPLv3).

## Documentation

Not yet. SQL language is well known and WebUI is simple. I'm relying on that for now. Of course documentation is on my to-do list. 

## Releases

QuestDB is a massive re-write of what use to be NFSdb. This is a Beta software and so far there are no releases.
