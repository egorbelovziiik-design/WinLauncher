export default function Loading() {
  return (
    <div className="space-y-4">
      <div className="skeleton h-8 w-52 rounded-lg" />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => <div key={i} className="skeleton h-24 rounded-xl" />)}
      </div>
      <div className="skeleton h-80 rounded-xl" />
    </div>
  );
}
