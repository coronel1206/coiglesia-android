CREATE OR REPLACE FUNCTION public.listar_personas_autorizadas_app()
RETURNS TABLE(id uuid, nombres character varying, apellidos character varying, tipo_documento character varying, numero_documento character varying, telefono character varying, correo_electronico character varying, fecha_nacimiento date, estado character varying, congregacion_id uuid, congregacion_nombre character varying, distrito_id uuid, distrito_nombre character varying)
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $function$

WITH personas_autorizadas AS (
SELECT DISTINCT ON (p.id)
p.id,
p.nombres,
p.apellidos,
p.tipo_documento,
p.numero_documento,
p.telefono,
p.correo_electronico,
p.fecha_nacimiento,
p.estado,
c.id AS congregacion_id,
c.nombre AS congregacion_nombre,
d.id AS distrito_id,
d.nombre AS distrito_nombre,
m.fecha_ingreso,
m.fecha_registro

FROM public.personas p

JOIN public.membresias_congregacionales m
ON m.persona_id = p.id

JOIN public.congregaciones c
ON c.id = m.congregacion_id

JOIN public.distritos d
ON d.id = c.distrito_id

WHERE p.estado = 'activo'
AND c.estado = 'activa'
AND d.estado = 'activo'

AND m.estado IN ('activa', 'finalizada')

AND public.tiene_permiso_territorial_seguro(
'personas.consultar',
d.id,
c.id
)

ORDER BY
p.id,
m.fecha_ingreso DESC,
m.fecha_registro DESC,
m.id DESC
)

SELECT
pa.id,
pa.nombres,
pa.apellidos,
pa.tipo_documento,
pa.numero_documento,
pa.telefono,
pa.correo_electronico,
pa.fecha_nacimiento,
pa.estado,
pa.congregacion_id,
pa.congregacion_nombre,
pa.distrito_id,
pa.distrito_nombre

FROM personas_autorizadas pa

ORDER BY pa.apellidos, pa.nombres;

$function$
;
