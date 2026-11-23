# La inactividad reinicia la expiración del ensamblado

Status: accepted

Complementa ADR-0002. El plazo de cinco minutos se cuenta desde el último fragmento recibido, no desde el primero: cada fragmento nuevo del grupo —remitente más `messageId`— reinicia el contador. Elegimos esta ventana deslizante porque un mensaje puede viajar en varios SMS con retrasos variables y descartar el grupo por el mero paso del tiempo desde el primer fragmento perdería mensajes que todavía siguen llegando. La consecuencia es que un grupo abandonado debe liberarse por inactividad y no por una fecha fija de creación.
