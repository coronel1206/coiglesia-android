CREATE OR REPLACE FUNCTION public.tiene_acceso_app()
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $function$
SELECT EXISTS (
SELECT 1
FROM public.usuarios u
JOIN public.personas p
ON p.id = u.persona_id
JOIN public.asignaciones_cargos ac
ON ac.persona_id = p.id
JOIN public.cargos c
ON c.id = ac.cargo_id
WHERE u.id = (SELECT auth.uid())
AND u.estado = 'activo'
AND p.estado = 'activo'
AND ac.estado = 'activo'
AND ac.fecha_inicio <= CURRENT_DATE
AND (
ac.fecha_fin IS NULL
OR ac.fecha_fin >= CURRENT_DATE
)
AND c.activo = TRUE
AND (
(
c.nivel = 'nacional'
AND ac.distrito_id IS NULL
AND ac.congregacion_id IS NULL
)
OR (
c.nivel = 'distrital'
AND ac.distrito_id IS NOT NULL
AND ac.congregacion_id IS NULL
AND EXISTS (
SELECT 1
FROM public.distritos d
WHERE d.id = ac.distrito_id
AND d.estado = 'activo'
)
)
OR (
c.nivel = 'local'
AND ac.congregacion_id IS NOT NULL
AND ac.distrito_id IS NULL
AND EXISTS (
SELECT 1
FROM public.congregaciones cg
JOIN public.distritos d
ON d.id = cg.distrito_id
WHERE cg.id = ac.congregacion_id
AND cg.estado = 'activa'
AND d.estado = 'activo'
)
)
)
);
$function$
;
