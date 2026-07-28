| Descrizione       | Sintassi Esatta (copia letteralmente)                                                           |
|-------------------|-------------------------------------------------------------------------------------------------|
| Write a file      | `<call:file_write filename="nome_file" approval="required">contenuto</call>`                    |
| Read a file       | `<call:file_read filename="nome_file"></call>`                                                  |
| Delete a file     | `<call:file_delete filename="nome_file" approval="required"></call>` or delete by pattern: `<call:file_delete foldername="cartella" pattern="*.txt" approval="required"></call>` |
| Append to file    | `<call:file_append filename="nome_file" approval="required">testo</call>`                       |
| Move / Rename     | `<call:file_move src="sorgente" dest="destinazione" approval="required"></call>`                |
| Copy a file       | `<call:file_copy src="sorgente" dest="destinazione"></call>`                                    |
| File exists?      | `<call:file_exists filename="nome_file"></call>`                                                |
| File info         | `<call:file_info filename="nome_file"></call>`                                                  |
| List files        | `<call:files_list foldername="nome_cartella"></call>` or filtrando per dimensione minima (byte, esclusa): `<call:files_list foldername="nome_cartella" min_size="2048"></call>` |
| Delete files      | `<call:files_delete foldername="cartella" pattern="*" approval="required"></call>`              |
| Folder exists?    | `<call:dir_exists foldername="nome_cartella"></call>`                                           |
| Create folder     | `<call:dir_create foldername="nome_cartella"></call>`                                           |
| Delete folder     | `<call:dir_delete foldername="nome_cartella" approval="required"></call>`                       |
| Search in files   | `<call:files_search foldername="." pattern="testo_da_cercare"></call>`                          |
| Find files by name| `<call:files_find foldername="cartella" pattern="*.csv"></call>`                                |
| Files in common   | `<call:files_common foldername="cartella" pattern="*.csv"></call>`                              |
| Compress          | `<call:file_compress src="sorgente" dest="archivio.zip"></call>`                                |
| Decompress        | `<call:file_decompress src="archivio.zip" dest="cartella_destinazione"></call>`                 |
| Append memory     | `<call:memory_append approval="required">testo da aggiungere</call>`                            |