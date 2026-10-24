export default function Loading() {
  return (
    <div className="container-x py-12">
      <div className="skeleton h-8 w-48 rounded-lg" />
      <div className="skeleton mt-4 h-4 w-80 rounded" />
      <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[0, 1, 2, 3, 4, 5].map((i) => <div key={i} className="skeleton h-72 rounded-2xl" />)}
      </div>
    </div>
  );
}
