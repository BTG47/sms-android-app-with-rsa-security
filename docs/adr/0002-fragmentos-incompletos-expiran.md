# Los fragmentos incompletos expiran en memoria a los cinco minutos

Status: accepted

Los SMS pueden llegar desordenados, duplicados o incompletos. Decidimos agrupar los fragmentos por remitente e identificador de mensaje, mantenerlos solo en memoria, expirarlos a los cinco minutos y mostrar el estado *incompleto* sin intentar descifrado. Descartamos persistirlos en esta primera versión porque el contrato lo permite solo si los reinicios lo exigen, y hacerlo añadiría almacenamiento, expiración en disco y limpieza que el MVP no necesita. Límites acordados: 99 fragmentos por mensaje y tope de memoria por ensamblado.
